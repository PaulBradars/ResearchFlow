package researchflow.visualization;

import org.junit.jupiter.api.Test;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisResult;
import researchflow.domain.EvidenceBundle;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChartBuilderTest {
    @Test
    void buildsABarChartFromAFrequencyResult() {
        var result = new AnalysisResult.Frequency(
                List.of(new AnalysisResult.Frequency.Category("Morning", 3, 60.0),
                        new AnalysisResult.Frequency.Category("Evening", 2, 40.0)), 5, 0);
        var evidence = evidence(AnalysisMethod.FREQUENCY, result, List.of("Study time"));

        var chart = ChartBuilder.build(evidence, List.of(), null);

        assertTrue(chart.isPresent());
        var bar = assertInstanceOf(ChartSpec.Bar.class, chart.get());
        assertEquals(List.of("Morning", "Evening"), bar.categories());
        assertEquals(List.of(3.0, 2.0), bar.values());
    }

    @Test
    void buildsAHistogramFromRawNumericValuesNotJustTheSummary() {
        var result = new AnalysisResult.NumericSummary(8, 0, 6.5, 6.5, 1.2, 5, 8);
        var evidence = evidence(AnalysisMethod.NUMERIC_SUMMARY, result, List.of("Sleep hours"));

        var chart = ChartBuilder.build(evidence, List.of(5.0, 6.0, 6.0, 7.0, 7.0, 8.0, 8.0, 8.0), null);

        assertTrue(chart.isPresent());
        var histogram = assertInstanceOf(ChartSpec.Histogram.class, chart.get());
        assertEquals(histogram.binLabels().size(), histogram.binCounts().size());
        assertEquals(8, histogram.binCounts().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void returnsEmptyForAHistogramWithNoRawValues() {
        var result = new AnalysisResult.NumericSummary(0, 3, 0, 0, 0, 0, 0);
        var evidence = evidence(AnalysisMethod.NUMERIC_SUMMARY, result, List.of("Sleep hours"));

        assertFalse(ChartBuilder.build(evidence, List.of(), null).isPresent());
    }

    @Test
    void buildsAScatterChartFromPairedRawValues() {
        var result = new AnalysisResult.Correlation(3, 1.0);
        var evidence = evidence(AnalysisMethod.CORRELATION, result, List.of("Sleep hours", "Focus"));

        var chart = ChartBuilder.build(evidence, List.of(1.0, 2.0, 3.0), List.of(2.0, 4.0, 6.0));

        assertTrue(chart.isPresent());
        var scatter = assertInstanceOf(ChartSpec.Scatter.class, chart.get());
        assertEquals(List.of(1.0, 2.0, 3.0), scatter.xValues());
        assertEquals(List.of(2.0, 4.0, 6.0), scatter.yValues());
    }

    @Test
    void flattensACrossTabulationIntoABarOfCombinedCategories() {
        var result = new AnalysisResult.CrossTabulation(List.of("Evening", "Morning"), List.of("No", "Yes"),
                List.of(List.of(1, 0), List.of(0, 1)), 2);
        var evidence = evidence(AnalysisMethod.CROSS_TABULATION, result, List.of("Study time", "Mood"));

        var chart = assertInstanceOf(ChartSpec.Bar.class, ChartBuilder.build(evidence, List.of(), null).orElseThrow());

        assertEquals(4, chart.categories().size());
        assertTrue(chart.categories().contains("Evening / No"));
    }

    @Test
    void buildsATwoBarChartFromAGroupComparison() {
        var result = new AnalysisResult.GroupComparison("Morning", 4, 34.0, 3.16, "Evening", 4, 24.0, 3.16,
                10.0, 5.0, 8.0, 3.16);
        var evidence = evidence(AnalysisMethod.GROUP_COMPARISON, result, List.of("Focus", "Study time"));

        var chart = assertInstanceOf(ChartSpec.Bar.class, ChartBuilder.build(evidence, List.of(), null).orElseThrow());

        assertEquals(List.of("Morning", "Evening"), chart.categories());
        assertEquals(List.of(34.0, 24.0), chart.values());
    }

    private static EvidenceBundle evidence(AnalysisMethod method, AnalysisResult result, List<String> variableLabels) {
        var variables = variableLabels.stream().map(label -> new EvidenceBundle.VariableRef(UUID.randomUUID(), label)).toList();
        return new EvidenceBundle(UUID.randomUUID(), method, variables, List.of(), 5, result, List.of(), UUID.randomUUID());
    }
}

