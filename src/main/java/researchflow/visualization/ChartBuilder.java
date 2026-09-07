package researchflow.visualization;

import researchflow.domain.AnalysisResult;
import researchflow.domain.EvidenceBundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Chooses and builds the chart for one method's already-computed result: bar for frequency,
 * histogram for a numeric summary, scatter for correlation, and a flattened bar for cross-tabulation
 * and group comparison (grouped/box-plot charts remain optional per the reduced-MVP chart set).
 * Histogram and scatter need the raw, version-bound values behind the aggregated statistics —
 * {@code primaryValues}/{@code secondaryValues} come from {@code AnalysisService.chartValues}, read
 * from the same immutable dataset-version snapshot the analysis itself was computed against.
 */
public final class ChartBuilder {
    private static final int MAX_BINS = 10;

    private ChartBuilder() { }

    public static Optional<ChartSpec> build(EvidenceBundle evidence, List<Double> primaryValues, List<Double> secondaryValues) {
        var title = evidence.method() + ": " + variableLabel(evidence, 0)
                + (evidence.variables().size() > 1 ? " vs " + variableLabel(evidence, 1) : "");
        return switch (evidence.result()) {
            case AnalysisResult.Frequency value -> Optional.of(frequencyBar(title, variableLabel(evidence, 0), value));
            case AnalysisResult.NumericSummary ignored -> primaryValues.isEmpty() ? Optional.empty()
                    : Optional.of(histogram(title, variableLabel(evidence, 0), primaryValues));
            case AnalysisResult.Correlation ignored ->
                    (primaryValues.isEmpty() || secondaryValues == null || secondaryValues.isEmpty()) ? Optional.empty()
                    : Optional.of(new ChartSpec.Scatter(title, variableLabel(evidence, 0), variableLabel(evidence, 1),
                            primaryValues, secondaryValues));
            case AnalysisResult.CrossTabulation value -> Optional.of(crossTabulationBar(title, value));
            case AnalysisResult.GroupComparison value -> Optional.of(groupComparisonBar(title, value));
        };
    }

    private static ChartSpec.Bar frequencyBar(String title, String xLabel, AnalysisResult.Frequency value) {
        var categories = value.categories().stream().map(AnalysisResult.Frequency.Category::value).toList();
        var counts = value.categories().stream().map(category -> (double) category.count()).toList();
        return new ChartSpec.Bar(title, xLabel, "Count", categories, counts);
    }

    private static ChartSpec.Bar crossTabulationBar(String title, AnalysisResult.CrossTabulation value) {
        var categories = new ArrayList<String>();
        var counts = new ArrayList<Double>();
        for (int row = 0; row < value.rowLabels().size(); row++) {
            for (int column = 0; column < value.columnLabels().size(); column++) {
                categories.add(value.rowLabels().get(row) + " / " + value.columnLabels().get(column));
                counts.add((double) value.counts().get(row).get(column));
            }
        }
        return new ChartSpec.Bar(title, "Group", "Count", categories, counts);
    }

    private static ChartSpec.Bar groupComparisonBar(String title, AnalysisResult.GroupComparison value) {
        return new ChartSpec.Bar(title, "Group", "Mean",
                List.of(value.groupALabel(), value.groupBLabel()), List.of(value.groupAMean(), value.groupBMean()));
    }

    private static ChartSpec.Histogram histogram(String title, String xLabel, List<Double> values) {
        var min = Collections.min(values);
        var max = Collections.max(values);
        if (max <= min) return new ChartSpec.Histogram(title, xLabel, List.of(format(min)), List.of(values.size()));

        var binCount = Math.max(1, Math.min(MAX_BINS, (int) Math.ceil(Math.sqrt(values.size()))));
        var width = (max - min) / binCount;
        var counts = new int[binCount];
        for (var value : values) {
            var index = (int) ((value - min) / width);
            counts[Math.min(Math.max(index, 0), binCount - 1)]++;
        }
        var labels = new ArrayList<String>();
        var boxedCounts = new ArrayList<Integer>();
        for (int index = 0; index < binCount; index++) {
            labels.add(format(min + index * width) + "–" + format(min + (index + 1) * width));
            boxedCounts.add(counts[index]);
        }
        return new ChartSpec.Histogram(title, xLabel, labels, boxedCounts);
    }

    private static String variableLabel(EvidenceBundle evidence, int index) {
        return index < evidence.variables().size() ? evidence.variables().get(index).label() : "";
    }

    private static String format(double value) {
        var rounded = Math.round(value * 100) / 100.0;
        return rounded == Math.floor(rounded) ? Long.toString((long) rounded) : String.valueOf(rounded);
    }
}
