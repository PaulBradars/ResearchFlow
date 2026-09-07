package researchflow.ui;

import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import researchflow.domain.AnalysisResult;
import researchflow.domain.EvidenceBundle;

/**
 * Read-only rendering of a computed {@link EvidenceBundle} — the method, variables, sample size,
 * warnings, and the typed result. Shared by the manual analysis panel and Ask Your Data, so both
 * present evidence identically.
 */
final class EvidenceView {
    private EvidenceView() { }

    static Parent render(EvidenceBundle bundle) {
        var root = new VBox(10);
        var header = new VBox(4);
        header.getChildren().add(bold(bundle.method().toString()));
        header.getChildren().add(new Label("Variables: " + bundle.variables().stream()
                .map(EvidenceBundle.VariableRef::label).reduce((a, b) -> a + ", " + b).orElse("")));
        header.getChildren().add(new Label("Sample size: " + bundle.sampleSize()));
        root.getChildren().add(header);
        if (!bundle.warnings().isEmpty()) {
            var warnings = new VBox(2);
            for (var warning : bundle.warnings()) {
                var label = new Label("⚠ " + warning);
                label.getStyleClass().add("muted");
                label.setWrapText(true);
                warnings.getChildren().add(label);
            }
            root.getChildren().add(warnings);
        }
        root.getChildren().add(renderResult(bundle.result()));
        return root;
    }

    private static Parent renderResult(AnalysisResult result) {
        return switch (result) {
            case AnalysisResult.Frequency value -> renderFrequency(value);
            case AnalysisResult.NumericSummary value -> renderNumericSummary(value);
            case AnalysisResult.Correlation value -> renderCorrelation(value);
            case AnalysisResult.CrossTabulation value -> renderCrossTabulation(value);
            case AnalysisResult.GroupComparison value -> renderGroupComparison(value);
        };
    }

    private static Parent renderFrequency(AnalysisResult.Frequency value) {
        var box = new VBox(4);
        for (var category : value.categories()) {
            box.getChildren().add(new Label(category.value() + ": " + category.count() + " (" + round(category.percentage()) + "%)"));
        }
        if (value.missingCount() > 0) box.getChildren().add(muted("Missing: " + value.missingCount()));
        return box;
    }

    private static Parent renderNumericSummary(AnalysisResult.NumericSummary value) {
        var box = new VBox(4);
        box.getChildren().add(new Label("Count: " + value.count() + "   Missing: " + value.missingCount()));
        box.getChildren().add(new Label("Mean: " + round(value.mean()) + "   Median: " + round(value.median())));
        box.getChildren().add(new Label("Standard deviation: " + round(value.standardDeviation())));
        box.getChildren().add(new Label("Minimum: " + round(value.minimum()) + "   Maximum: " + round(value.maximum())));
        return box;
    }

    private static Parent renderCorrelation(AnalysisResult.Correlation value) {
        return new Label("n = " + value.count() + "   Pearson r = " + round(value.coefficient()));
    }

    private static Parent renderCrossTabulation(AnalysisResult.CrossTabulation value) {
        var grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(6);
        for (int column = 0; column < value.columnLabels().size(); column++) {
            grid.add(bold(value.columnLabels().get(column)), column + 1, 0);
        }
        for (int row = 0; row < value.rowLabels().size(); row++) {
            grid.add(bold(value.rowLabels().get(row)), 0, row + 1);
            for (int column = 0; column < value.columnLabels().size(); column++) {
                grid.add(new Label(Integer.toString(value.counts().get(row).get(column))), column + 1, row + 1);
            }
        }
        return grid;
    }

    private static Parent renderGroupComparison(AnalysisResult.GroupComparison value) {
        var box = new VBox(4);
        box.getChildren().add(new Label(value.groupALabel() + ": n=" + value.groupACount() + ", mean=" + round(value.groupAMean())
                + ", SD=" + round(value.groupASD())));
        box.getChildren().add(new Label(value.groupBLabel() + ": n=" + value.groupBCount() + ", mean=" + round(value.groupBMean())
                + ", SD=" + round(value.groupBSD())));
        box.getChildren().add(new Label("Mean difference: " + round(value.meanDifference())));
        box.getChildren().add(new Label("Welch t = " + round(value.tStatistic()) + ", df = " + round(value.degreesOfFreedom())));
        box.getChildren().add(new Label("Cohen's d (effect size) = " + round(value.cohensD())));
        return box;
    }

    private static Label bold(String text) {
        var label = new Label(text);
        label.getStyleClass().add("question-label");
        return label;
    }

    private static Label muted(String text) {
        var label = new Label(text);
        label.getStyleClass().add("muted");
        return label;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
