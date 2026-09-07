package researchflow.service;

import researchflow.domain.AnalysisResult;
import researchflow.domain.EvidenceBundle;
import researchflow.domain.Finding;
import researchflow.persistence.FindingRepository;
import researchflow.visualization.ChartSpec;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Draft/edit/approve/reject findings. A finding's {@code analysisId}/{@code datasetVersionId} are
 * fixed at creation and never change — editing {@link #edit} only ever touches the wording.
 * Unapproved findings are excluded from the report by default (see {@code ReportService}).
 */
public final class FindingService {
    private final FindingRepository findings;

    public FindingService(FindingRepository findings) {
        this.findings = findings;
    }

    /** Drafts a finding from a just-computed evidence bundle, with an optional chart derived from the same version. */
    public Finding draft(UUID studyId, EvidenceBundle evidence, String text, ChartSpec chart) {
        var normalized = requireText(text);
        var id = findings.create(studyId, evidence.id(), evidence.datasetVersionId(), normalized,
                evidenceSummary(evidence), chart);
        return require(id);
    }

    public Finding edit(UUID findingId, String text) {
        findings.updateText(findingId, requireText(text));
        return require(findingId);
    }

    public Finding approve(UUID findingId) {
        findings.approve(findingId);
        return require(findingId);
    }

    public Finding reject(UUID findingId) {
        findings.reject(findingId);
        return require(findingId);
    }

    public List<Finding> list(UUID studyId) {
        return findings.findByStudy(studyId);
    }

    /** A short, plain-language starting point for a finding's text; always researcher-editable before saving. */
    public static String draftText(EvidenceBundle evidence) {
        return switch (evidence.result()) {
            case AnalysisResult.Frequency ignored ->
                    "Distribution of " + label(evidence, 0) + " (n=" + evidence.sampleSize() + ").";
            case AnalysisResult.NumericSummary value -> label(evidence, 0) + " has a mean of " + round(value.mean())
                    + " (SD=" + round(value.standardDeviation()) + ", n=" + evidence.sampleSize() + ").";
            case AnalysisResult.Correlation value -> label(evidence, 0) + " and " + label(evidence, 1)
                    + " show a correlation of r=" + round(value.coefficient()) + " (n=" + evidence.sampleSize() + ").";
            case AnalysisResult.CrossTabulation ignored ->
                    "Cross-tabulation of " + label(evidence, 0) + " and " + label(evidence, 1)
                            + " (n=" + evidence.sampleSize() + ").";
            case AnalysisResult.GroupComparison value -> label(evidence, 0) + " differs between " + value.groupALabel()
                    + " (mean=" + round(value.groupAMean()) + ") and " + value.groupBLabel() + " (mean="
                    + round(value.groupBMean()) + "); mean difference=" + round(value.meanDifference()) + ".";
        };
    }

    /** A short metadata line for the report, e.g. "n=24 · CORRELATION · Sleep hours, Focus". */
    public static String evidenceSummary(EvidenceBundle evidence) {
        var variables = evidence.variables().stream().map(EvidenceBundle.VariableRef::label)
                .reduce((a, b) -> a + ", " + b).orElse("");
        return "n=" + evidence.sampleSize() + " · " + evidence.method() + " · " + variables;
    }

    private Finding require(UUID findingId) {
        return findings.findById(findingId)
                .orElseThrow(() -> new IllegalArgumentException("Finding not found: " + findingId));
    }

    private static String label(EvidenceBundle evidence, int index) {
        return index < evidence.variables().size() ? evidence.variables().get(index).label() : "variable";
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private static String requireText(String text) {
        var normalized = text == null ? "" : text.strip();
        if (normalized.length() < 10) throw new ValidationException(Map.of("text", "Provide at least 10 characters."));
        if (normalized.length() > 2_000) throw new ValidationException(Map.of("text", "Use 2,000 characters or fewer."));
        return normalized;
    }
}
