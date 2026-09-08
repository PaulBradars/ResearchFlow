package researchflow.ui;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import researchflow.domain.Study;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

public final class StudyEditorDialog extends Dialog<StudyEditorDialog.Values> {
    private final TextField title = new TextField();
    private final TextField researcher = new TextField();
    private final TextArea description = new TextArea();
    private final TextArea objectives = new TextArea();
    private final TextArea questions = new TextArea();
    private final DatePicker startDate = new DatePicker();
    private final DatePicker endDate = new DatePicker();
    private final Label error = new Label();

    public StudyEditorDialog(Study existing) {
        setTitle(existing == null ? "Create study" : "Edit study");
        setHeaderText(existing == null ? "Create a research workspace" : "Update study details");
        getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        getDialogPane().setPrefWidth(680);

        description.setPrefRowCount(3);
        objectives.setPrefRowCount(3);
        questions.setPrefRowCount(4);
        questions.setPromptText("One research question per line");
        error.getStyleClass().add("field-error");

        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(8));
        grid.addRow(0, new Label("Title *"), title);
        grid.addRow(1, new Label("Researcher"), researcher);
        grid.addRow(2, new Label("Description"), description);
        grid.addRow(3, new Label("Objectives"), objectives);
        grid.addRow(4, new Label("Research questions"), questions);
        grid.addRow(5, new Label("Start date"), startDate);
        grid.addRow(6, new Label("End date"), endDate);
        grid.add(error, 1, 7);
        GridPane.setHgrow(title, javafx.scene.layout.Priority.ALWAYS);
        getDialogPane().setContent(grid);

        if (existing != null) {
            title.setText(existing.title());
            researcher.setText(existing.researcher());
            description.setText(existing.description());
            objectives.setText(existing.objectives());
            questions.setText(String.join(System.lineSeparator(), existing.researchQuestions()));
            startDate.setValue(existing.startDate());
            endDate.setValue(existing.endDate());
        } else {
            startDate.setValue(LocalDate.now());
        }

        var ok = getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            var message = localValidation();
            if (message != null) {
                error.setText(message);
                event.consume();
            }
        });
        setResultConverter(button -> button == ButtonType.OK ? values() : null);
    }

    private String localValidation() {
        if (title.getText() == null || title.getText().isBlank()) {
            return "Study title is required.";
        }
        if (startDate.getValue() != null && endDate.getValue() != null
                && endDate.getValue().isBefore(startDate.getValue())) {
            return "End date cannot be before the start date.";
        }
        return null;
    }

    private Values values() {
        var questionList = Arrays.stream(questions.getText().split("\\R"))
                .map(String::strip).filter(value -> !value.isBlank()).toList();
        return new Values(title.getText(), description.getText(), objectives.getText(), researcher.getText(),
                startDate.getValue(), endDate.getValue(), questionList);
    }

    public record Values(String title, String description, String objectives, String researcher,
                         LocalDate startDate, LocalDate endDate, List<String> researchQuestions) {
    }
}

