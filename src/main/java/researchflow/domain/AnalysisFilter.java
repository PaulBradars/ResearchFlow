package researchflow.domain;

import java.util.UUID;

public record AnalysisFilter(UUID questionId, DatasetFilterOperator operator, String value) { }

