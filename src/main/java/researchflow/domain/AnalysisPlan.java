package researchflow.domain;

import java.util.List;
import java.util.UUID;

/**
 * A researcher- or (later) AI-proposed analysis request. {@code datasetVersionId} is {@code null}
 * to mean "the Study's active version, creating a baseline snapshot first if none exists yet" —
 * once resolved, every stored analysis is bound to one explicit, immutable version.
 */
public record AnalysisPlan(AnalysisMethod method, UUID primaryVariableId, UUID secondaryVariableId,
                          List<AnalysisFilter> filters, UUID datasetVersionId) {
    public AnalysisPlan {
        filters = filters == null ? List.of() : List.copyOf(filters);
    }
}
