package researchflow.ui;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import researchflow.domain.Question;
import researchflow.domain.QuestionOption;
import researchflow.domain.QuestionType;

import java.util.Arrays;

public final class QuestionEditorDialog {
    private QuestionEditorDialog() { }

    public static Dialog<Question> create(Question existing) {
        var dialog = new Dialog<Question>();
        dialog.setTitle(existing == null ? "Add question" : "Edit question");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        var variable = new TextField(existing == null ? "q_" : existing.variableKey());
        var label = new TextField(existing == null ? "" : existing.label());
        var help = new TextField(existing == null ? "" : existing.helpText());
        var type = new ComboBox<QuestionType>();
        type.getItems().setAll(QuestionType.values());
        type.setValue(existing == null ? QuestionType.SHORT_TEXT : existing.type());
        var required = new CheckBox("Required");
        required.setSelected(existing != null && existing.required());
        var minimum = new TextField(existing == null || existing.minimum() == null ? "" : existing.minimum().toString());
        var maximum = new TextField(existing == null || existing.maximum() == null ? "" : existing.maximum().toString());
        var options = new TextArea(existing == null ? "" : String.join("\n",
                existing.options().stream().map(QuestionOption::label).toList()));
        options.setPromptText("One option per line");
        options.setPrefRowCount(5);
        var hint = new Label("Options are required for choice, Likert, and rating questions.");
        hint.getStyleClass().add("muted");
        var validation = new Label(); validation.getStyleClass().add("field-error");
        var grid = new GridPane();
        grid.setHgap(12); grid.setVgap(10); grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Variable key"), variable);
        grid.addRow(1, new Label("Question"), label);
        grid.addRow(2, new Label("Help text"), help);
        grid.addRow(3, new Label("Type"), type);
        grid.addRow(4, new Label(""), required);
        grid.addRow(5, new Label("Minimum"), minimum);
        grid.addRow(6, new Label("Maximum"), maximum);
        grid.addRow(7, new Label("Options"), options);
        grid.add(hint, 1, 8);
        grid.add(validation, 1, 9);
        dialog.getDialogPane().setContent(grid);
        var ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.disableProperty().bind(label.textProperty().isEmpty().or(variable.textProperty().isEmpty()));
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                var min = minimum.getText().isBlank() ? null : Double.valueOf(minimum.getText().strip());
                var max = maximum.getText().isBlank() ? null : Double.valueOf(maximum.getText().strip());
                if (min != null && max != null && max < min) throw new IllegalArgumentException("Maximum must be at least minimum.");
                if (!variable.getText().strip().matches("[A-Za-z][A-Za-z0-9_]{0,63}"))
                    throw new IllegalArgumentException("Use a letter followed by letters, numbers, or underscores.");
                var optionCount = Arrays.stream(options.getText().split("\\R")).map(String::strip)
                        .filter(value -> !value.isBlank()).count();
                if (switch (type.getValue()) {
                    case SINGLE_CHOICE, MULTIPLE_CHOICE, LIKERT, RATING -> optionCount < 2;
                    default -> false;
                }) throw new IllegalArgumentException("Add at least two options for this question type.");
                validation.setText("");
            } catch (NumberFormatException exception) {
                validation.setText("Minimum and maximum must be valid numbers."); event.consume();
            } catch (IllegalArgumentException exception) {
                validation.setText(exception.getMessage()); event.consume();
            }
        });
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            try {
                var min = minimum.getText().isBlank() ? null : Double.valueOf(minimum.getText().strip());
                var max = maximum.getText().isBlank() ? null : Double.valueOf(maximum.getText().strip());
                var parsedOptions = Arrays.stream(options.getText().split("\\R"))
                        .map(String::strip).filter(value -> !value.isBlank()).map(QuestionOption::create).toList();
                return existing == null
                        ? Question.create(variable.getText(), label.getText(), help.getText(), type.getValue(),
                        required.isSelected(), min, max, parsedOptions)
                        : existing.revise(variable.getText(), label.getText(), help.getText(), type.getValue(),
                        required.isSelected(), min, max, parsedOptions);
            } catch (NumberFormatException exception) {
                return null;
            }
        });
        return dialog;
    }
}

