package researchflow.analysis;

import org.junit.jupiter.api.Test;
import researchflow.domain.*;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StatisticalBreakdownTest {
    @Test
    void correlationIncludesPairedDescriptivesPrecisionAndIdentifierCaution() {
        var evidence = evidence(AnalysisMethod.CORRELATION, new AnalysisResult.Correlation(3, -0.012345));
        var text = StatisticalBreakdown.render(evidence, List.of(1d, 2d, 3d), List.of(20d, 30d, 40d));
        assertTrue(text.contains("Pearson r: -0.0123"));
        assertTrue(text.contains("Direction: negative"));
        assertTrue(text.contains("r squared: 0.0002"));
        assertTrue(text.contains("CustomerID: n=3; mean=2.0000; median=2.0000; sample SD=1.0000"));
        assertTrue(text.contains("Age: n=3; mean=30.0000"));
        assertTrue(text.contains("arbitrary record key"));
        assertTrue(text.contains("No p-value or confidence interval was computed"));
        assertTrue(text.contains("Snapshot warning"));
    }

    @Test
    void constantVariablesAndInsufficientPairsDoNotClaimZeroCorrelation() {
        var evidence = evidence(AnalysisMethod.CORRELATION, new AnalysisResult.Correlation(3, 0));
        var text = StatisticalBreakdown.render(evidence, List.of(1d, 1d, 1d), List.of(20d, 30d, 40d));
        assertTrue(text.contains("not estimable"));
        assertFalse(text.contains("Direction: zero"));
        var empty = StatisticalBreakdown.render(evidence(AnalysisMethod.CORRELATION,
                new AnalysisResult.Correlation(0, 0)), List.of(), List.of());
        assertTrue(empty.contains("no valid values"));
    }

    @Test
    void crossTabReportsDenominatorsAndHandlesEmptyRows() {
        var result = new AnalysisResult.CrossTabulation(List.of("A", "B"), List.of("Yes", "No"),
                List.of(List.of(3, 1), List.of(0, 0)), 4);
        var text = StatisticalBreakdown.render(evidence(AnalysisMethod.CROSS_TABULATION, result), List.of(), List.of());
        assertTrue(text.contains("Yes: 3; row 75.0000%; total 75.0000%"));
        assertTrue(text.contains("No: 0; row not estimable"));
        assertFalse(text.contains("NaN"));
    }

    @Test
    void groupComparisonDoesNotPresentUndefinedStandardizedStatisticsAsZero() {
        var result = new AnalysisResult.GroupComparison("A", 3, 2, 0, "B", 3, 1, 0, 1, 0, 4, 0);
        var text = StatisticalBreakdown.render(evidence(AnalysisMethod.GROUP_COMPARISON, result), List.of(), List.of());
        assertTrue(text.contains("Mean difference (first group minus second): 1.0000"));
        assertTrue(text.contains("Welch t: not estimable"));
        assertTrue(text.contains("Cohen's d (pooled SD): not estimable"));
    }

    @Test
    void numericAndFrequencyReportsDoNotHideMissingnessOrUndefinedStatistics() {
        var numeric = StatisticalBreakdown.render(evidence(AnalysisMethod.NUMERIC_SUMMARY,
                new AnalysisResult.NumericSummary(0, 5, 0, 0, 0, 0, 0)), List.of(), List.of());
        assertTrue(numeric.contains("missing: 5"));
        assertTrue(numeric.contains("No valid numeric observations"));
        assertFalse(numeric.contains("Mean: 0"));
        var frequency = StatisticalBreakdown.render(evidence(AnalysisMethod.FREQUENCY,
                new AnalysisResult.Frequency(List.of(new AnalysisResult.Frequency.Category("Yes", 3, 75)), 4, 2)),
                List.of(), List.of());
        assertTrue(frequency.contains("Yes — 3 — 75.0000%"));
        assertTrue(frequency.contains("missing: 2"));
    }

    private static EvidenceBundle evidence(AnalysisMethod method, AnalysisResult result) {
        return new EvidenceBundle(UUID.randomUUID(), method,
                List.of(new EvidenceBundle.VariableRef(UUID.randomUUID(), "CustomerID"),
                        new EvidenceBundle.VariableRef(UUID.randomUUID(), "Age")),
                List.of(), AnalysisResults.sampleSizeOf(result), result, List.of("Snapshot warning"), UUID.randomUUID());
    }
}
