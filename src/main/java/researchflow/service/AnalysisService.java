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

public final class AnalysisService {
    private final FormRepository forms;
    private final VersionRepository versions;
    private final AnalysisRepository analyses;
    private final Map<AnalysisMethod, AnalysisStrategy> strategies = AnalysisStrategyRegistry.buildDefault();

    public AnalysisService(FormRepository forms, VersionRepository versions, AnalysisRepository analyses) {
        this.forms = forms;
        this.versions = versions;
        this.analyses = analyses;
    }

    public EvidenceBundle run(UUID studyId, AnalysisPlan plan) {
        return run(studyId, plan, "MANUAL", null);
    }

    public EvidenceBundle run(UUID studyId, AnalysisPlan plan, String source, ModelMetadata modelMetadata) {
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
        var warnings = AnalysisWarnings.compute(plan.method(), sampleSize, filtered.size());
        var id = analyses.save(studyId, version.id(), plan, result, sampleSize, warnings, source, modelMetadata);
        return new EvidenceBundle(id, plan.method(), variableRefs(plan, questions), plan.filters(), sampleSize,
                result, warnings, version.id());
    }

    public List<AnalysisSummary> history(UUID studyId) {
        return analyses.findByStudy(studyId);
    }

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

    public ChartValues chartValues(UUID studyId, EvidenceBundle evidence) {
        var reconstructedPlan = new AnalysisPlan(evidence.method(), evidence.variables().getFirst().questionId(),
                evidence.variables().size() > 1 ? evidence.variables().get(1).questionId() : null,
                evidence.filters(), evidence.datasetVersionId());
        return chartValues(studyId, reconstructedPlan, evidence.datasetVersionId());
    }

    public record ChartValues(List<Double> primary, List<Double> secondary) { }

    public StoredAnalysis details(UUID analysisId) {
        return analyses.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
    }

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

