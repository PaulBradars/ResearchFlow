package researchflow.domain;

import java.util.List;
import java.util.UUID;

public record AnalysisPlan(AnalysisMethod method, UUID primaryVariableId, UUID secondaryVariableId,
                          List<AnalysisFilter> filters, UUID datasetVersionId) {
    public AnalysisPlan {
        filters = filters == null ? List.of() : List.copyOf(filters);
    }
}

