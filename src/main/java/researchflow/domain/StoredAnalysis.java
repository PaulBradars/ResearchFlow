package researchflow.domain;

import java.time.Instant;
import java.util.UUID;

/** A persisted analysis record as stored: raw plan/result/warnings JSON for a details view. */
public record StoredAnalysis(UUID id, UUID studyId, UUID datasetVersionId, AnalysisMethod method,
                             String planJson, String resultJson, int sampleSize, String warningsJson,
                             String source, Instant createdAt, int schemaVersion, String variablesJson, String modelMetadataJson) { }
