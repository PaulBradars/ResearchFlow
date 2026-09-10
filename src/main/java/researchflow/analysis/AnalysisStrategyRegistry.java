package researchflow.analysis;

import researchflow.domain.AnalysisMethod;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Simple Factory / registry for the reduced-MVP analysis strategies. */
public final class AnalysisStrategyRegistry {
    private AnalysisStrategyRegistry() { }

    public static Map<AnalysisMethod, AnalysisStrategy> buildDefault() {
        return register(List.of(new FrequencyStrategy(), new NumericSummaryStrategy(),
                new CorrelationStrategy(), new CrossTabulationStrategy(), new GroupComparisonStrategy()));
    }

    /** Fail at composition time, rather than silently replacing a strategy or failing during a user request. */
    public static Map<AnalysisMethod, AnalysisStrategy> register(java.util.Collection<? extends AnalysisStrategy> implementations) {
        var strategies = new EnumMap<AnalysisMethod, AnalysisStrategy>(AnalysisMethod.class);
        for (var strategy : implementations) {
            java.util.Objects.requireNonNull(strategy, "strategy");
            var method = java.util.Objects.requireNonNull(strategy.method(), "strategy method");
            if (strategies.putIfAbsent(method, strategy) != null) {
                throw new IllegalArgumentException("Duplicate analysis strategy: " + method);
            }
        }
        for (var method : AnalysisMethod.values()) if (!strategies.containsKey(method))
            throw new IllegalArgumentException("Missing analysis strategy: " + method);
        return Collections.unmodifiableMap(strategies);
    }
}
