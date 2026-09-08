package researchflow.domain;

import java.time.Instant;
import java.util.UUID;

public record AnalysisSummary(UUID id, AnalysisMethod method, UUID datasetVersionId, int versionNumber,
                              int sampleSize, String source, Instant createdAt) { }

