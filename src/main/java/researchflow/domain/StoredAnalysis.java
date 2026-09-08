package researchflow.domain;

import java.time.Instant;
import java.util.UUID;

public record StoredAnalysis(UUID id, UUID studyId, UUID datasetVersionId, AnalysisMethod method,
                             String planJson, String resultJson, int sampleSize, String warningsJson,
                             String source, Instant createdAt) { }

