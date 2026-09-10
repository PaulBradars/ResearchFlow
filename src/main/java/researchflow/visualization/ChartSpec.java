package researchflow.visualization;

import java.util.List;

public sealed interface ChartSpec permits ChartSpec.Bar, ChartSpec.Histogram, ChartSpec.Scatter {
    String title();

    record Bar(String title, String xLabel, String yLabel, List<String> categories, List<Double> values)
            implements ChartSpec {
        public Bar {
            categories = List.copyOf(categories);
            values = List.copyOf(values);
        }
    }

    record Histogram(String title, String xLabel, List<String> binLabels, List<Integer> binCounts)
            implements ChartSpec {
        public Histogram {
            binLabels = List.copyOf(binLabels);
            binCounts = List.copyOf(binCounts);
        }
    }

    record Scatter(String title, String xLabel, String yLabel, List<Double> xValues, List<Double> yValues)
            implements ChartSpec {
        public Scatter {
            xValues = List.copyOf(xValues);
            yValues = List.copyOf(yValues);
        }
    }
}
