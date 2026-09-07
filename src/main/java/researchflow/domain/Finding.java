package researchflow.domain;

import researchflow.visualization.ChartSpec;

import java.time.Instant;
import java.util.UUID;

/**
 * A researcher-reviewed statement drawn from one analysis. {@code analysisId}/{@code datasetVersionId}
 * never change once created — editing {@link #text()} does not move the finding to a different
 * analysis or version. {@code chart} is {@code null} when no chart could be derived (e.g. a
 * cross-tabulation with no pairwise-complete data).
 */
public record Finding(UUID id, UUID studyId, UUID analysisId, UUID datasetVersionId, String text,
                      String evidenceSummary, ChartSpec chart, FindingStatus status,
                      Instant createdAt, Instant updatedAt, Instant approvedAt) { }
