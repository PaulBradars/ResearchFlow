package researchflow.service;

import researchflow.analysis.AnalysisContext;
import researchflow.analysis.AnalysisData;
import researchflow.analysis.AnalysisPlanValidator;
import researchflow.analysis.AnalysisResults;
import researchflow.analysis.AnalysisStrategy;
import researchflow.analysis.AnalysisStrategyRegistry;
import researchflow.analysis.AnalysisWarnings;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.AnalysisSummary;
import researchflow.domain.Answer;
import researchflow.domain.DatasetVersion;
import researchflow.domain.EvidenceBundle;
import researchflow.domain.ModelMetadata;
import researchflow.domain.Question;
import researchflow.domain.StoredAnalysis;
import researchflow.persistence.AnalysisRepository;
import researchflow.persistence.FormRepository;
import researchflow.persistence.VersionRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The manual analysis facade: interpret (schema-aware validation) -> resolve an explicit dataset
 * version -> choose a strategy -> execute -> store evidence. {@code AnalysisFacade} (AI) fronts this
 * with natural-language plan generation but never bypasses validation or execution here.
 */
public final class AnalysisService {
    private final StudyWriteGuard writeGuard;
    private final FormRepository forms;
    private final VersionRepository versions;
    private final AnalysisRepository analyses;
    private final Map<AnalysisMethod, AnalysisStrategy> strategies;

    public AnalysisService(FormRepository forms, VersionRepository versions, AnalysisRepository analyses, StudyWriteGuard writeGuard) {
        this(forms, versions, analyses, writeGuard, AnalysisStrategyRegistry.buildDefault().values());
    }

    /** Strategies are replaceable at composition time; every implementation still passes through the same guards. */
    public AnalysisService(FormRepository forms, VersionRepository versions, AnalysisRepository analyses,
                           StudyWriteGuard writeGuard, java.util.Collection<? extends AnalysisStrategy> strategies) {
        this.writeGuard = java.util.Objects.requireNonNull(writeGuard);
        this.forms = forms;
        this.versions = versions;
        this.analyses = analyses;
        this.strategies = AnalysisStrategyRegistry.register(strategies);
    }

    public void requireWritableStudy(UUID studyId) { writeGuard.requireWritable(studyId); }

    public EvidenceBundle run(UUID studyId, AnalysisPlan plan) {
        return run(studyId, plan, "MANUAL", null);
    }

    /**
     * The full pipeline behind both manual analysis and "Ask Your Data": validate -> resolve
     * version -> execute -> persist. {@code source} is {@code "MANUAL"} or {@code "AI"};
     * {@code modelMetadata} is {@code null} for manual analyses.
     */
    public EvidenceBundle run(UUID studyId, AnalysisPlan plan, String source, ModelMetadata modelMetadata) {
        writeGuard.requireWritable(studyId);
        var questions = questionsById(studyId);
        AnalysisPlanValidator.validate(plan, questions);
        var version = resolveVersion(studyId, plan.datasetVersionId());
        var raw = analyses.loadVersionData(version.id());
        var grouped = AnalysisData.group(raw);
        var filtered = AnalysisData.applyFilters(grouped, plan.filters(), questions);
        var context = new AnalysisContext(plan, questions, filtered);
        var strategy = strategies.get(plan.method());
        var result = strategy.execute(context);
        var sampleSize = AnalysisResults.sampleSizeOf(result);
        var warnings = new ArrayList<>(AnalysisWarnings.compute(plan.method(), sampleSize, filtered.size()));
        if (plan.datasetVersionId() == null) {
            var freshness = versions.freshness(studyId);
            if (freshness == researchflow.domain.DatasetFreshness.STALE) {
                warnings.add("Live data differs from the active dataset version. This analysis uses the saved snapshot; "
                        + "create a new version to include live changes.");
            }
        }
        if (!version.membershipComplete()) {
            warnings.add("This legacy snapshot did not record complete response membership; blank responses "
                    + "may be absent from its population. Create a new baseline for complete membership.");
        }
        var id = analyses.save(studyId, version.id(), plan, result, sampleSize, warnings, source, modelMetadata);
        return new EvidenceBundle(id, plan.method(), variableRefs(plan, questions), plan.filters(), sampleSize,
                result, warnings, version.id());
    }

    public List<AnalysisSummary> history(UUID studyId) {
        return analyses.findByStudy(studyId);
    }

    /**
     * Re-derives the raw, version-bound numeric values behind a plan's variable(s) for charts and
     * supplemental paired descriptive statistics. The primary result stays in the stored evidence.
     * {@code resolvedVersionId} must be the explicit version a prior {@link #run} resolved to, so the
     * chart is guaranteed to reflect the exact same immutable snapshot as the analysis it illustrates.
     */
    public ChartValues chartValues(UUID studyId, AnalysisPlan plan, UUID resolvedVersionId) {
        var questions = questionsById(studyId);
        var raw = analyses.loadVersionData(resolvedVersionId);
        var grouped = AnalysisData.group(raw);
        var filtered = AnalysisData.applyFilters(grouped, plan.filters(), questions);
        var primary = new ArrayList<Double>();
        var secondary = plan.secondaryVariableId() == null ? null : new ArrayList<Double>();
        for (var response : filtered) {
            if (!(response.get(plan.primaryVariableId()) instanceof Answer.Number primaryNumber)) continue;
            if (plan.secondaryVariableId() == null) {
                primary.add(primaryNumber.value());
            } else if (response.get(plan.secondaryVariableId()) instanceof Answer.Number secondaryNumber) {
                primary.add(primaryNumber.value());
                secondary.add(secondaryNumber.value());
            }
        }
        return new ChartValues(List.copyOf(primary), secondary == null ? null : List.copyOf(secondary));
    }

    /**
     * Convenience overload for callers that only have the resulting {@code EvidenceBundle} (e.g.
     * "Ask Your Data", which never sees the AI-derived plan directly) — reconstructs an equivalent
     * plan from the bundle's own recorded variables/filters/version, which is always exact.
     */
    public ChartValues chartValues(UUID studyId, EvidenceBundle evidence) {
        var reconstructedPlan = new AnalysisPlan(evidence.method(), evidence.variables().getFirst().questionId(),
                evidence.variables().size() > 1 ? evidence.variables().get(1).questionId() : null,
                evidence.filters(), evidence.datasetVersionId());
        return chartValues(studyId, reconstructedPlan, evidence.datasetVersionId());
    }

    /** Raw values for one (or a paired two) numeric variable(s), scoped to the exact same filtered, version-bound data. */
    public record ChartValues(List<Double> primary, List<Double> secondary) { }

    public researchflow.domain.HistoricalEvidence reopen(UUID studyId, UUID analysisId) {
        var stored = details(analysisId);
        if (!stored.studyId().equals(studyId)) throw new IllegalArgumentException("Analysis is not part of this study.");
        return researchflow.analysis.EvidenceDecoder.decode(stored);
    }

    public String provenance(UUID studyId, UUID analysisId) {
        var historical = reopen(studyId, analysisId);
        var stored = historical.stored();
        var version = versions.findByStudy(studyId).stream().filter(v -> v.id().equals(stored.datasetVersionId())).findFirst().orElseThrow();
        var active = versions.findActive(studyId).orElse(null);
        var versionState = active == null || !active.id().equals(version.id()) ? "Historical version; differs from active selection. Live equality with this older evidence has not been evaluated"
                : "Active snapshot / live comparison: " + versions.freshness(studyId);
        return "Analysis " + stored.id() + " | version v" + version.versionNumber() + " (" + version.id() + ")"
                + " | snapshot created " + version.createdAt() + " | analysis created " + stored.createdAt()
                + " | n=" + stored.sampleSize() + " | source=" + stored.source() + " | " + versionState
                + (historical.supported() ? " | filters=" + historical.plan().filters() + " | warnings=" + historical.evidence().warnings() : "")
                + (historical.notice().isBlank() ? "" : " | " + historical.notice());
    }

    public StoredAnalysis details(UUID analysisId) {
        return analyses.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
    }

    /** {@code null} resolves to the Study's active version, auto-creating a baseline snapshot if none exists yet. */
    private DatasetVersion resolveVersion(UUID studyId, UUID requestedVersionId) {
        if (requestedVersionId != null) {
            return versions.findByStudy(studyId).stream().filter(version -> version.id().equals(requestedVersionId))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("The dataset version no longer exists."));
        }
        return versions.findActive(studyId).orElseGet(() ->
                versions.createSnapshot(studyId, "Baseline snapshot (auto-created for analysis)", ""));
    }

    private Map<UUID, Question> questionsById(UUID studyId) {
        var map = new LinkedHashMap<UUID, Question>();
        for (var form : forms.findByStudy(studyId)) {
            for (var question : form.questions()) map.put(question.id(), question);
        }
        return map;
    }

    private static List<EvidenceBundle.VariableRef> variableRefs(AnalysisPlan plan, Map<UUID, Question> questions) {
        var refs = new ArrayList<EvidenceBundle.VariableRef>();
        refs.add(new EvidenceBundle.VariableRef(plan.primaryVariableId(), label(questions, plan.primaryVariableId())));
        if (plan.secondaryVariableId() != null) {
            refs.add(new EvidenceBundle.VariableRef(plan.secondaryVariableId(), label(questions, plan.secondaryVariableId())));
        }
        return List.copyOf(refs);
    }

    private static String label(Map<UUID, Question> questions, UUID questionId) {
        var question = questions.get(questionId);
        return question == null ? "" : question.label();
    }
}
