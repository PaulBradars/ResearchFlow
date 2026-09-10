package researchflow.analysis;

import org.junit.jupiter.api.Test;
import researchflow.domain.AnalysisMethod;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisStrategyRegistryTest {
    @Test void defaultRegistryIsCompleteAndImmutable() {
        var registry = AnalysisStrategyRegistry.buildDefault();
        assertEquals(AnalysisMethod.values().length, registry.size());
        for (var entry : registry.entrySet()) assertEquals(entry.getKey(), entry.getValue().method());
        assertThrows(UnsupportedOperationException.class, registry::clear);
    }

    @Test void rejectsMissingAndDuplicateImplementationsAtCompositionTime() {
        assertThrows(IllegalArgumentException.class, () -> AnalysisStrategyRegistry.register(List.of()));
        var implementations = new ArrayList<>(AnalysisStrategyRegistry.buildDefault().values());
        implementations.add(new FrequencyStrategy());
        assertThrows(IllegalArgumentException.class, () -> AnalysisStrategyRegistry.register(implementations));
    }
}
