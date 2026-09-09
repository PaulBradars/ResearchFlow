package researchflow.domain;

import java.time.Instant;
import java.util.List;

/** Everything a generated report needs: composed on demand from currently stored data, never persisted itself. */
public record ReportDocument(Study study, DatasetVersion activeVersion, long formCount, long responseCount,
                             QualitySummary qualitySummary, List<Finding> approvedFindings, List<String> limitations,
                             Instant generatedAt, java.util.Map<java.util.UUID, String> findingProvenance, DatasetFreshness liveFreshness) {
    public ReportDocument {
        findingProvenance = java.util.Map.copyOf(findingProvenance);
        approvedFindings = List.copyOf(approvedFindings);
        limitations = List.copyOf(limitations);
    }

    public record QualitySummary(long openIssues, long acceptedIssues, long deferredIssues, long resolvedIssues) { }
}
