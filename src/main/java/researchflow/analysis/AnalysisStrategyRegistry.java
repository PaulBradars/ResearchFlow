package researchflow.analysis;

import researchflow.domain.AnalysisMethod;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class AnalysisStrategyRegistry {
    private AnalysisStrategyRegistry() { }

    public static Map<AnalysisMethod, AnalysisStrategy> buildDefault() {
        var strategies = new EnumMap<AnalysisMethod, AnalysisStrategy>(AnalysisMethod.class);
        for (var strategy : List.<AnalysisStrategy>of(new FrequencyStrategy(), new NumericSummaryStrategy(),
                new CorrelationStrategy(), new CrossTabulationStrategy(), new GroupComparisonStrategy())) {
            strategies.put(strategy.method(), strategy);
        }
        return Collections.unmodifiableMap(strategies);
    }
}

