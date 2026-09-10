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

    /** Named construction for optional plan parts; semantic validation remains in AnalysisPlanValidator. */
    public static Builder builder(AnalysisMethod method, UUID primaryVariableId) {
        return new Builder(method, primaryVariableId);
    }

    public static final class Builder {
        private final AnalysisMethod method;
        private final UUID primary;
        private UUID secondary;
        private List<AnalysisFilter> filters = List.of();
        private UUID version;

        private Builder(AnalysisMethod method, UUID primary) {
            this.method = java.util.Objects.requireNonNull(method, "method");
            this.primary = java.util.Objects.requireNonNull(primary, "primaryVariableId");
        }

        public Builder secondaryVariable(UUID value) { secondary = value; return this; }
        public Builder filters(List<AnalysisFilter> value) {
            filters = value == null ? List.of() : List.copyOf(value);
            return this;
        }
        /** null keeps the normal active-snapshot resolution policy. */
        public Builder datasetVersion(UUID value) { version = value; return this; }
        public AnalysisPlan build() { return new AnalysisPlan(method, primary, secondary, filters, version); }
    }
}
