package researchflow.ui;

import javafx.scene.Node;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
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
        var editor = editor(variable, currentValue);
        var reason = new TextArea(); reason.setPromptText("Why is this correction needed?"); reason.setPrefRowCount(3);
        var error = new Label(); error.getStyleClass().add("field-error");
        dialog.getDialogPane().setContent(new VBox(10, current, new Label("Replacement value"),
                editor.node(), new Label("Correction reason"), reason, error));
        var ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                var raw = editor.value().get().strip();
                if (raw.isBlank()) throw new IllegalArgumentException("A replacement value is required.");
                if (variable.type() == researchflow.domain.QuestionType.NUMBER) {
                    final double number;
                    try { number = Double.parseDouble(raw); }
                    catch (NumberFormatException invalid) { throw new IllegalArgumentException("Enter a valid number."); }
                    if (!Double.isFinite(number)) throw new IllegalArgumentException("Enter a finite number.");
                    if (variable.minimum() != null && number < variable.minimum())
                        throw new IllegalArgumentException("Enter a value of at least " + variable.minimum() + ".");
                    if (variable.maximum() != null && number > variable.maximum())
                        throw new IllegalArgumentException("Enter a value no greater than " + variable.maximum() + ".");
                }
                if (variable.type() == researchflow.domain.QuestionType.DATE) {
                    try { java.time.LocalDate.parse(raw); }
                    catch (java.time.format.DateTimeParseException invalid) { throw new IllegalArgumentException("Enter a valid date as YYYY-MM-DD."); }
                }
                if (reason.getText().strip().length() < 3) throw new IllegalArgumentException("Provide a reason of at least 3 characters.");
                if (reason.getText().strip().length() > 1_000) throw new IllegalArgumentException("Use 1,000 characters or fewer for the reason.");
                error.setText("");
            } catch (IllegalArgumentException invalid) {
                error.setText(invalid.getMessage()); event.consume();
            }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new Values(editor.value().get(), reason.getText().strip()) : null);
        return dialog;
    }

    private static Editor editor(DatasetVariable variable, String currentValue) {
        return switch (variable.type()) {
            case DATE -> {
                // ISO text avoids DatePicker's locale-dependent, uncommitted typed-value behavior.
                var input = new TextField(currentValue); input.setPromptText("YYYY-MM-DD");
                yield new Editor(input, input::getText);
            }
            case SINGLE_CHOICE, LIKERT, RATING -> {
                var input = new ComboBox<String>(); input.getItems().setAll(variable.options());
                input.setPromptText("Choose an option");
                if (variable.options().contains(currentValue)) input.setValue(currentValue);
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
                if (input.getItems().contains(currentValue)) input.setValue(currentValue);
                yield new Editor(input, () -> input.getValue() == null ? "" : input.getValue());
            }
            case NUMBER, SHORT_TEXT -> {
                var input = new TextField(currentValue);
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
