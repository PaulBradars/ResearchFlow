package researchflow.ui;

import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ScatterChart;
import javafx.scene.chart.XYChart;
import researchflow.visualization.ChartSpec;

import java.util.List;

/** Renders a {@link ChartSpec} with native JavaFX chart controls, for in-app display. */
final class ChartView {
    private ChartView() { }

    static Node render(ChartSpec chart) {
        return switch (chart) {
            case ChartSpec.Bar bar -> barChart(bar.title(), bar.xLabel(), bar.yLabel(), bar.categories(), bar.values());
            case ChartSpec.Histogram histogram -> barChart(histogram.title(), histogram.xLabel(), "Count",
                    histogram.binLabels(), histogram.binCounts().stream().map(Integer::doubleValue).toList());
            case ChartSpec.Scatter scatter -> scatterChart(scatter);
        };
    }

    private static Node barChart(String title, String xLabel, String yLabel, List<String> categories, List<Double> values) {
        var xAxis = new CategoryAxis();
        xAxis.setLabel(xLabel);
        var yAxis = new NumberAxis();
        yAxis.setLabel(yLabel);
        var chart = new BarChart<String, Number>(xAxis, yAxis);
        chart.setTitle(title);
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(280);
        var series = new XYChart.Series<String, Number>();
        for (int index = 0; index < categories.size(); index++) {
            series.getData().add(new XYChart.Data<>(categories.get(index), values.get(index)));
        }
        chart.getData().add(series);
        return chart;
    }

    private static Node scatterChart(ChartSpec.Scatter scatter) {
        var xAxis = new NumberAxis();
        xAxis.setLabel(scatter.xLabel());
        var yAxis = new NumberAxis();
        yAxis.setLabel(scatter.yLabel());
        var chart = new ScatterChart<Number, Number>(xAxis, yAxis);
        chart.setTitle(scatter.title());
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(280);
        var series = new XYChart.Series<Number, Number>();
        for (int index = 0; index < scatter.xValues().size(); index++) {
            series.getData().add(new XYChart.Data<>(scatter.xValues().get(index), scatter.yValues().get(index)));
        }
        chart.getData().add(series);
        return chart;
    }
}
