package researchflow.ui;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import researchflow.visualization.ChartSpec;

final class FindingEditorDialog {
    private FindingEditorDialog() { }

    static Dialog<String> create(String defaultText, ChartSpec chart) {
        var dialog = new Dialog<String>();
        dialog.setTitle("Create finding");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        var text = new TextArea(defaultText);
        text.setPrefRowCount(4);
        text.setWrapText(true);
        var error = new Label();
        error.getStyleClass().add("field-error");

        var content = new VBox(10, new Label("Finding text (editable)"), text);
        if (chart != null) {
            content.getChildren().add(new Label("Chart preview"));
            content.getChildren().add(ChartView.render(chart));
        }
        content.getChildren().add(error);
        dialog.getDialogPane().setContent(content);

        var ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (text.getText().strip().length() < 10) {
                error.setText("Provide at least 10 characters.");
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? text.getText().strip() : null);
        return dialog;
    }
}
