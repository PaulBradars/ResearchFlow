package researchflow.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import researchflow.domain.Form;
import researchflow.domain.FormStatus;
import researchflow.domain.Question;
import researchflow.domain.Section;
import researchflow.domain.Study;
import researchflow.service.FormService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public final class FormWorkspaceView {
    private final VBox root = new VBox(16);
    private final ListView<Form> forms = new ListView<>();
    private final VBox editor = new VBox(14);
    private final FormService service;
    private final Async async;
    private final Study study;
    private final Consumer<Form> collect;
    private final Consumer<Throwable> errors;
    private Form selected;

    public FormWorkspaceView(Study study, FormService service, Async async,
                             Consumer<Form> collect, Consumer<Throwable> errors) {
        this.study = study; this.service = service; this.async = async; this.collect = collect; this.errors = errors;
        root.setPadding(new Insets(28));
        var eyebrow = new Label("FORM DESIGN / RESEARCHER MODE"); eyebrow.getStyleClass().add("eyebrow");
        var title = new Label("Forms"); title.getStyleClass().add("page-title");
        var create = new Button("New form"); create.getStyleClass().add("primary-button");
        create.setOnAction(event -> createForm());
        var heading = new HBox(12, title, create);
        forms.setPlaceholder(new Label("No forms yet. Create a draft to begin."));
        forms.setCellFactory(list -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(Form item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.title() + "  ·  " + item.status());
            }
        });
        forms.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, value) -> showForm(value));
        var loading = new Label("Loading forms…"); editor.getChildren().setAll(loading);
        var split = new SplitPane(forms, editor); split.setDividerPositions(0.28);
        VBox.setVgrow(split, javafx.scene.layout.Priority.ALWAYS);
        root.getChildren().addAll(eyebrow, heading, split);
        reload(null);
    }

    public Parent node() { return root; }

    private void reload(java.util.UUID selectId) {
        async.run(() -> service.list(study.id()), values -> {
            forms.getItems().setAll(values);
            if (values.isEmpty()) showForm(null);
            else forms.getSelectionModel().select(values.stream().filter(form -> form.id().equals(selectId))
                    .findFirst().orElse(values.getFirst()));
        }, errors);
    }

    private void createForm() {
        var dialog = new Dialog<FormValues>();
        dialog.setTitle("New form"); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        var title = new TextField(); title.setPromptText("Form title");
        var description = new TextArea(); description.setPromptText("Description"); description.setPrefRowCount(4);
        dialog.getDialogPane().setContent(new VBox(10, new Label("Title"), title, new Label("Description"), description));
        dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(title.textProperty().isEmpty());
        dialog.setResultConverter(button -> button == ButtonType.OK ? new FormValues(title.getText(), description.getText()) : null);
        dialog.showAndWait().ifPresent(values -> async.run(
                () -> service.create(study.id(), values.title(), values.description()), form -> reload(form.id()), errors));
    }

    private void showForm(Form form) {
        selected = form;
        if (form == null) {
            editor.getChildren().setAll(new Label("Select a form or create a new draft."));
            return;
        }
        var title = new Label(form.title()); title.getStyleClass().add("section-title");
        var status = new Label(form.status() + " · version " + form.version()); status.getStyleClass().add("muted");
        var table = questionTable(form);
        var add = new Button("Add question");
        var edit = new Button("Edit");
        var delete = new Button("Delete");
        var up = new Button("Move up");
        var down = new Button("Move down");
        var preview = new Button("Preview");
        var activate = new Button("Activate"); activate.getStyleClass().add("primary-button");
        var enter = new Button("Collect response"); enter.getStyleClass().add("primary-button");
        var close = new Button("Close form");
        var draft = form.status() == FormStatus.DRAFT;
        add.setDisable(!draft); edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull().or(new javafx.beans.property.SimpleBooleanProperty(!draft)));
        delete.disableProperty().bind(edit.disableProperty()); up.disableProperty().bind(edit.disableProperty()); down.disableProperty().bind(edit.disableProperty());
        activate.setDisable(!draft); preview.setDisable(form.questions().isEmpty());
        enter.setDisable(form.status() != FormStatus.ACTIVE); close.setDisable(form.status() != FormStatus.ACTIVE);
        add.setOnAction(event -> QuestionEditorDialog.create(null).showAndWait().ifPresent(question -> mutateQuestions(values -> values.add(question))));
        edit.setOnAction(event -> QuestionEditorDialog.create(table.getSelectionModel().getSelectedItem()).showAndWait()
                .ifPresent(question -> mutateQuestions(values -> values.set(values.indexOf(table.getSelectionModel().getSelectedItem()), question))));
        delete.setOnAction(event -> mutateQuestions(values -> values.remove(table.getSelectionModel().getSelectedItem())));
        up.setOnAction(event -> move(table.getSelectionModel().getSelectedItem(), -1));
        down.setOnAction(event -> move(table.getSelectionModel().getSelectedItem(), 1));
        preview.setOnAction(event -> preview(form));
        activate.setOnAction(event -> async.run(() -> service.activate(form.id()), updated -> reload(updated.id()), errors));
        close.setOnAction(event -> async.run(() -> service.close(form.id()), updated -> reload(updated.id()), errors));
        enter.setOnAction(event -> collect.accept(form));
        var controls = new HBox(8, add, edit, delete, up, down, preview, activate, enter, close);
        editor.setPadding(new Insets(0, 0, 0, 18));
        editor.getChildren().setAll(title, status, new Label(form.description().isBlank() ? "No description." : form.description()), controls, table);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
    }

    private TableView<Question> questionTable(Form form) {
        var table = new TableView<Question>();
        var key = new TableColumn<Question, String>("Variable"); key.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().variableKey()));
        var label = new TableColumn<Question, String>("Question"); label.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().label()));
        var type = new TableColumn<Question, String>("Type"); type.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().type().toString()));
        var required = new TableColumn<Question, String>("Required"); required.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().required() ? "Yes" : "No"));
        table.getColumns().addAll(key, label, type, required);
        table.getItems().setAll(form.questions());
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("This draft has no questions yet."));
        return table;
    }

    private void move(Question question, int delta) {
        if (question == null) return;
        mutateQuestions(values -> {
            int from = values.indexOf(question), to = from + delta;
            if (from >= 0 && to >= 0 && to < values.size()) java.util.Collections.swap(values, from, to);
        });
    }

    private void mutateQuestions(Consumer<ArrayList<Question>> mutation) {
        if (selected == null) return;
        var questions = new ArrayList<>(selected.questions());
        mutation.accept(questions);
        var original = selected.sections().isEmpty() ? Section.create("Questions") : selected.sections().getFirst();
        var sections = List.of(original.withQuestions(questions));
        async.run(() -> service.updateStructure(selected.id(), selected.title(), selected.description(), sections),
                updated -> reload(updated.id()), errors);
    }

    private void preview(Form form) {
        var content = new VBox(14);
        content.setPadding(new Insets(12));
        var title = new Label(form.title()); title.getStyleClass().add("page-title");
        content.getChildren().add(title);
        if (!form.description().isBlank()) content.getChildren().add(new Label(form.description()));
        for (var section : form.sections()) {
            if (!section.title().isBlank()) {
                var heading = new Label(section.title()); heading.getStyleClass().add("section-title");
                content.getChildren().add(heading);
            }
            section.questions().stream().map(QuestionControlFactory::create)
                    .map(QuestionControlFactory.QuestionControl::node).forEach(content.getChildren()::add);
        }
        var scroll = new javafx.scene.control.ScrollPane(content);
        scroll.setFitToWidth(true); scroll.setPrefViewportWidth(700); scroll.setPrefViewportHeight(620);
        var dialog = new Dialog<Void>(); dialog.setTitle("Form preview");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(scroll); dialog.showAndWait();
    }

    private record FormValues(String title, String description) { }
}
