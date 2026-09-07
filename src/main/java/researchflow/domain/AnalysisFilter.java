package researchflow.domain;

import java.util.UUID;

/** A single structured filter applied before an analysis runs. Reuses the Dataset workspace's operator set. */
public record AnalysisFilter(UUID questionId, DatasetFilterOperator operator, String value) { }
