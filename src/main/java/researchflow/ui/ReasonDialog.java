package researchflow.ui;

import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

/** A small confirmation dialog collecting a required 3–1,000 character reason/note. */
public final class ReasonDialog {
    private ReasonDialog() { }

    public static Dialog<String> create(String title, String prompt, String promptText) {
        var dialog = new Dialog<String>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        var label = new Label(prompt);
        label.setWrapText(true);
        var text = new TextArea();
        text.setPromptText(promptText);
        text.setPrefRowCount(3);
        var error = new Label();
        error.getStyleClass().add("field-error");
        dialog.getDialogPane().setContent(new VBox(10, label, text, error));
        var ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            var value = text.getText() == null ? "" : text.getText().strip();
            if (value.length() < 3) {
                error.setText("Provide at least 3 characters.");
                event.consume();
            }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK ? text.getText().strip() : null);
        return dialog;
    }
}
