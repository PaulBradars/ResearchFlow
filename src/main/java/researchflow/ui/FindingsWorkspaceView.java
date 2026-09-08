package researchflow.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import researchflow.domain.Finding;
import researchflow.domain.FindingStatus;
import researchflow.domain.Study;
import researchflow.service.FindingService;
import researchflow.service.ValidationException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

public final class FindingsWorkspaceView {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final VBox root = new VBox(12);
    private final TableView<Finding> table = new TableView<>();
    private final ComboBox<FindingStatus> statusFilter = new ComboBox<>();
    private final Study study;
    private final FindingService service;
    private final Async async;
    private final Consumer<Throwable> errors;

    public FindingsWorkspaceView(Study study, FindingService service, Async async, Consumer<Throwable> errors) {
        this.study = study;
        this.service = service;
        this.async = async;
        this.errors = errors;
        root.setPadding(new Insets(20));

        var eyebrow = new Label("FINDINGS");
        eyebrow.getStyleClass().add("eyebrow");
        var title = new Label("Findings");
        title.getStyleClass().add("page-title");

        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(FindingStatus.values());
        statusFilter.setConverter(new javafx.util.StringConverter<FindingStatus>() {
            @Override public String toString(FindingStatus value) { return value == null ? "All statuses" : value.toString(); }
            @Override public FindingStatus fromString(String string) { return null; }
        });
        statusFilter.setValue(null);
        statusFilter.setOnAction(event -> refresh());
        var refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh());
        var toolbar = new HBox(10, new Label("Status"), statusFilter, refresh);
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        table.setPlaceholder(new Label("No findings yet. Create one from an analysis's evidence."));
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                review(table.getSelectionModel().getSelectedItem());
            }
        });
        buildColumns();
        var review = new Button("Review selected");
        review.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        review.setOnAction(event -> review(table.getSelectionModel().getSelectedItem()));

        root.getChildren().addAll(eyebrow, title, toolbar, table, review);
        VBox.setVgrow(table, Priority.ALWAYS);
        refresh();
    }

    public Parent node() {
        return root;
    }

    private void refresh() {
        table.setDisable(true);
        async.run(() -> service.list(study.id()), all -> {
            table.setDisable(false);
            var filter = statusFilter.getValue();
            table.getItems().setAll(filter == null ? all : all.stream().filter(finding -> finding.status() == filter).toList());
        }, errors);
    }

    private void buildColumns() {
        var text = new TableColumn<Finding, String>("Finding");
        text.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().text()));
        text.setPrefWidth(420);
        var summary = new TableColumn<Finding, String>("Evidence");
        summary.setCellValueFactory(row -> new SimpleStringProperty(
                row.getValue().evidenceSummary() == null ? "" : row.getValue().evidenceSummary()));
        var status = new TableColumn<Finding, String>("Status");
        status.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().status().toString()));
        var created = new TableColumn<Finding, String>("Created");
        created.setCellValueFactory(row -> new SimpleStringProperty(TIMESTAMP.format(row.getValue().createdAt())));
        table.getColumns().setAll(List.of(text, summary, status, created));
    }

    private void review(Finding finding) {
        if (finding == null) return;
        var content = new VBox(10);
        content.setPadding(new Insets(8));
        var editor = new TextArea(finding.text());
        editor.setPrefRowCount(4);
        editor.setWrapText(true);
        var error = new Label();
        error.getStyleClass().add("field-error");
        content.getChildren().addAll(new Label(finding.status() + " · " + (finding.evidenceSummary() == null ? "" : finding.evidenceSummary())),
                editor, error);
        if (finding.chart() != null) content.getChildren().add(ChartView.render(finding.chart()));

        var actions = new HBox(8);
        var save = new Button("Save wording");
        actions.getChildren().add(save);
        Button approve = null;
        Button reject = null;
        if (finding.status() != FindingStatus.APPROVED) {
            approve = new Button("Approve");
            actions.getChildren().add(approve);
        }
        if (finding.status() != FindingStatus.REJECTED) {
            reject = new Button("Reject");
            actions.getChildren().add(reject);
        }
        content.getChildren().add(actions);

        var dialog = new Dialog<Void>();
        dialog.setTitle("Review finding");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(content);
        save.setOnAction(event -> async.run(() -> service.edit(finding.id(), editor.getText()), ignored -> {
            dialog.close();
            refresh();
        }, failure -> {
            if (failure instanceof ValidationException issue) error.setText(String.join(" ", issue.errors().values()));
            else errors.accept(failure);
        }));
        if (approve != null) {
            approve.setOnAction(event -> async.run(() -> service.approve(finding.id()), ignored -> {
                dialog.close();
                refresh();
            }, errors));
        }
        if (reject != null) {
            reject.setOnAction(event -> async.run(() -> service.reject(finding.id()), ignored -> {
                dialog.close();
                refresh();
            }, errors));
        }
        dialog.showAndWait();
    }
}

