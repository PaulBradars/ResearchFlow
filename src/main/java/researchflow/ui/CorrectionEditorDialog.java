package researchflow.ui;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import researchflow.domain.DatasetVariable;
import researchflow.service.ResponseSubmissionService;

import java.util.function.Supplier;

public final class CorrectionEditorDialog {
    private CorrectionEditorDialog() { }

    public static Dialog<Values> create(DatasetVariable variable, String currentValue) {
        var dialog = new Dialog<Values>();
        dialog.setTitle("Correct “" + variable.label() + "”");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        var current = new Label("Current value: " + currentValue); current.getStyleClass().add("muted");
        var editor = editor(variable);
        var reason = new TextArea(); reason.setPromptText("Why is this correction needed?"); reason.setPrefRowCount(3);
        var error = new Label(); error.getStyleClass().add("field-error");
        dialog.getDialogPane().setContent(new VBox(10, current, new Label("Replacement value"),
                editor.node(), new Label("Correction reason"), reason, error));
        var ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (editor.value().get().isBlank()) { error.setText("A replacement value is required."); event.consume(); }
            else if (reason.getText().strip().length() < 3) { error.setText("Provide a reason of at least 3 characters."); event.consume(); }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new Values(editor.value().get(), reason.getText().strip()) : null);
        return dialog;
    }

    private static Editor editor(DatasetVariable variable) {
        return switch (variable.type()) {
            case DATE -> {
                var input = new DatePicker();
                yield new Editor(input, () -> input.getValue() == null ? "" : input.getValue().toString());
            }
            case SINGLE_CHOICE, LIKERT, RATING -> {
                var input = new ComboBox<String>(); input.getItems().setAll(variable.options());
                input.setPromptText("Choose an option");
                yield new Editor(input, () -> input.getValue() == null ? "" : input.getValue());
            }
            case MULTIPLE_CHOICE -> {
                var input = new ListView<String>(); input.getItems().setAll(variable.options());
                input.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); input.setPrefHeight(130);
                yield new Editor(input, () -> String.join(ResponseSubmissionService.MULTI_VALUE_SEPARATOR,
                        input.getSelectionModel().getSelectedItems()));
            }
            case YES_NO -> {
                var input = new ComboBox<String>(); input.getItems().setAll("Yes", "No");
                yield new Editor(input, () -> input.getValue() == null ? "" : input.getValue());
            }
            case NUMBER, SHORT_TEXT -> {
                var input = new TextField();
                var range = variable.minimum() == null && variable.maximum() == null ? "" : " ("
                        + (variable.minimum() == null ? "" : "min " + variable.minimum())
                        + (variable.minimum() != null && variable.maximum() != null ? ", " : "")
                        + (variable.maximum() == null ? "" : "max " + variable.maximum()) + ")";
                input.setPromptText(variable.type() == researchflow.domain.QuestionType.NUMBER ? "Enter a number" + range : "Replacement text");
                yield new Editor(input, input::getText);
            }
        };
    }

    private record Editor(Node node, Supplier<String> value) { }
    public record Values(String rawValue, String reason) { }
}

