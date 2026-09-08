package researchflow.ui;

import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import researchflow.domain.Question;
import researchflow.service.ResponseSubmissionService;

import java.util.ArrayList;
import java.util.function.Supplier;

public final class QuestionControlFactory {
    private QuestionControlFactory() { }

    public static QuestionControl create(Question question) {
        var title = new Label(question.label() + (question.required() ? " *" : ""));
        title.getStyleClass().add("question-label");
        var error = new Label();
        error.getStyleClass().add("field-error");
        error.setVisible(false);
        error.setManaged(false);
        var body = new VBox(6);
        Supplier<String> value;
        switch (question.type()) {
            case DATE -> {
                var input = new DatePicker();
                body.getChildren().add(input);
                value = () -> input.getValue() == null ? "" : input.getValue().toString();
            }
            case SINGLE_CHOICE, LIKERT, RATING -> {
                var input = new ComboBox<String>();
                input.getItems().setAll(question.options().stream().map(option -> option.value()).toList());
                input.setPromptText("Choose an option");
                body.getChildren().add(input);
                value = () -> input.getValue() == null ? "" : input.getValue();
            }
            case MULTIPLE_CHOICE -> {
                var checks = new ArrayList<CheckBox>();
                for (var option : question.options()) {
                    var check = new CheckBox(option.label());
                    check.setUserData(option.value());
                    checks.add(check);
                    body.getChildren().add(check);
                }
                value = () -> checks.stream().filter(CheckBox::isSelected)
                        .map(check -> check.getUserData().toString())
                        .collect(java.util.stream.Collectors.joining(ResponseSubmissionService.MULTI_VALUE_SEPARATOR));
            }
            case YES_NO -> {
                var input = new ComboBox<String>();
                input.getItems().setAll("Yes", "No");
                input.setPromptText("Choose Yes or No");
                body.getChildren().add(input);
                value = () -> input.getValue() == null ? "" : input.getValue();
            }
            case SHORT_TEXT, NUMBER -> {
                var input = new TextField();
                input.setPromptText(question.type() == researchflow.domain.QuestionType.NUMBER ? "Enter a number" : "Your answer");
                body.getChildren().add(input);
                value = input::getText;
            }
            default -> throw new IllegalStateException("Unsupported question type: " + question.type());
        }
        if (!question.helpText().isBlank()) {
            var help = new Label(question.helpText());
            help.getStyleClass().add("muted");
            help.setWrapText(true);
            body.getChildren().add(0, help);
        }
        body.getChildren().add(error);
        var root = new VBox(7, title, body);
        root.getStyleClass().add("question-card");
        return new QuestionControl(root, value, error);
    }

    public record QuestionControl(Parent node, Supplier<String> value, Label error) {
        public void showError(String message) {
            error.setText(message == null ? "" : message);
            error.setVisible(message != null && !message.isBlank());
            error.setManaged(error.isVisible());
        }
    }
}

