package researchflow.domain;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisPlanBuilderTest {
    @Test void defaultsAndNamedOptionsMatchTheRecordContract() {
        var primary = UUID.randomUUID();
        var secondary = UUID.randomUUID();
        var version = UUID.randomUUID();
        assertEquals(new AnalysisPlan(AnalysisMethod.FREQUENCY, primary, null, List.of(), null),
                AnalysisPlan.builder(AnalysisMethod.FREQUENCY, primary).build());
        var filters = List.of(new AnalysisFilter(primary, DatasetFilterOperator.GREATER_THAN, "5"));
        assertEquals(new AnalysisPlan(AnalysisMethod.CORRELATION, primary, secondary, filters, version),
                AnalysisPlan.builder(AnalysisMethod.CORRELATION, primary).secondaryVariable(secondary)
                        .filters(filters).datasetVersion(version).build());
    }

    @Test void reusingBuilderAndMutatingSourceCannotChangeBuiltPlans() {
        var primary = UUID.randomUUID();
        var filters = new ArrayList<AnalysisFilter>();
        filters.add(new AnalysisFilter(primary, DatasetFilterOperator.IS_MISSING, null));
        var builder = AnalysisPlan.builder(AnalysisMethod.FREQUENCY, primary).filters(filters);
        filters.clear();
        var first = builder.build();
        builder.filters(null).datasetVersion(UUID.randomUUID());
        assertEquals(1, first.filters().size());
        assertNull(first.datasetVersionId());
        assertTrue(builder.build().filters().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> first.filters().clear());
    }
}
