package researchflow.domain;

import researchflow.visualization.ChartSpec;

import java.time.Instant;
import java.util.UUID;

public record Finding(UUID id, UUID studyId, UUID analysisId, UUID datasetVersionId, String text,
                      String evidenceSummary, ChartSpec chart, FindingStatus status,
                      Instant createdAt, Instant updatedAt, Instant approvedAt) { }

