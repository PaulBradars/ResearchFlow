package researchflow.ui;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import researchflow.command.ReviewCommand;
import researchflow.domain.DatasetQuery;
import researchflow.domain.DatasetSort;
import researchflow.domain.QualityIssue;
import researchflow.domain.QualityIssueStatus;
import researchflow.domain.Study;
import researchflow.service.DatasetService;
import researchflow.service.QualityReviewService;
import researchflow.service.QualityService;
import researchflow.service.ValidationException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

public final class QualityIssuesView {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final VBox root = new VBox(12);
    private final TableView<QualityIssue> table = new TableView<>();
    private final ComboBox<QualityIssueStatus> statusFilter = new ComboBox<>();
    private final Label status = new Label();
    private final BusyState scanBusy = new BusyState();
    private final Study study;
    private final QualityService quality;
    private final QualityReviewService review;
    private final DatasetService datasets;
    private final Async async;
    private final Consumer<Throwable> errors;

    public QualityIssuesView(Study study, QualityService quality, QualityReviewService review,
                             DatasetService datasets, Async async, Consumer<Throwable> errors) {
        this.study = study;
        this.quality = quality;
        this.review = review;
        this.datasets = datasets;
        this.async = async;
        this.errors = errors;
        root.setPadding(new Insets(20));

        var eyebrow = new Label("QUALITY REVIEW");
        eyebrow.getStyleClass().add("eyebrow");
        var title = new Label("Quality issues");
        title.getStyleClass().add("page-title");

        statusFilter.getItems().addAll(QualityIssueStatus.values());
        statusFilter.setValue(QualityIssueStatus.OPEN);
        statusFilter.setOnAction(event -> refresh());
        var scan = new Button("Scan for quality issues");
        scan.getStyleClass().add("primary-button");
        scan.setOnAction(event -> scan());
        var toolbar = new HBox(10, new Label("Status"), statusFilter, scan, status, scanBusy.node());
        toolbar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        table.setPlaceholder(new Label("No quality issues in this status."));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                review(table.getSelectionModel().getSelectedItem());
            }
        });
        buildColumns();

        var reviewButton = new Button("Review selected");
        reviewButton.disableProperty().bind(Bindings.size(table.getSelectionModel().getSelectedItems()).isNotEqualTo(1));
        reviewButton.setOnAction(event -> review(table.getSelectionModel().getSelectedItem()));
        var acceptAll = new Button("Accept selected");
        var deferAll = new Button("Defer selected");
        var excludeAll = new Button("Exclude selected");
        for (var button : List.of(acceptAll, deferAll, excludeAll)) {
            button.disableProperty().bind(Bindings.isEmpty(table.getSelectionModel().getSelectedItems()));
        }
        acceptAll.setOnAction(event -> bulk("Accept", "Retain the selected data as-is.",
                "Why should these issues be accepted?", ReviewCommand.Accept::new));
        deferAll.setOnAction(event -> bulk("Defer", "Postpone review of the selected issues.",
                "Why are these issues being deferred?", ReviewCommand.Defer::new));
        excludeAll.setOnAction(event -> bulk("Exclude", "Exclude the affected responses from the dataset.",
                "Why are these responses being excluded?", this::excludeCommand));
        var actions = new HBox(8, reviewButton, acceptAll, deferAll, excludeAll);

        root.getChildren().addAll(eyebrow, title, toolbar, table, actions);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        refresh();
    }

    public Parent node() {
        return root;
    }

    private void buildColumns() {
        var type = new TableColumn<QualityIssue, String>("Type");
        type.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().type().toString()));
        var severity = new TableColumn<QualityIssue, String>("Severity");
        severity.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().severity().toString()));
        var responseId = new TableColumn<QualityIssue, String>("Response");
        responseId.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().responseId() == null
                ? "—" : row.getValue().responseId().toString().substring(0, 8)));
        var explanation = new TableColumn<QualityIssue, String>("Explanation");
        explanation.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().explanation()));
        explanation.setPrefWidth(420);
        var created = new TableColumn<QualityIssue, String>("Detected");
        created.setCellValueFactory(row -> new SimpleStringProperty(TIMESTAMP.format(row.getValue().createdAt())));
        table.getColumns().setAll(List.of(type, severity, responseId, explanation, created));
    }

    private void refresh() {
        table.setDisable(true);
        async.run(() -> quality.list(study.id(), statusFilter.getValue()), issues -> {
            table.setDisable(false);
            table.getItems().setAll(issues);
            status.setText(issues.size() + " issue(s)");
        }, errors);
    }

    private void scan() {
        table.setDisable(true);
        var task = async.run(() -> quality.scan(study.id()), issues -> {
            table.setDisable(false);
            scanBusy.finish();
            statusFilter.setValue(QualityIssueStatus.OPEN);
            table.getItems().setAll(quality.list(study.id(), QualityIssueStatus.OPEN));
            status.setText("Scan complete: " + table.getItems().size() + " open issue(s)");
        }, failure -> { table.setDisable(false); scanBusy.finish(); errors.accept(failure); });
        scanBusy.start(task, () -> table.setDisable(false));
    }

    private void review(QualityIssue issue) {
        if (issue == null) return;
        var content = new VBox(9);
        content.setPadding(new Insets(8));
        content.getChildren().add(new Label(issue.type() + " · " + issue.severity() + " · " + issue.status()));
        var explanation = new Label(issue.explanation());
        explanation.setWrapText(true);
        content.getChildren().add(explanation);
        if (issue.resolutionNote() != null && !issue.resolutionNote().isBlank()) {
            var note = new Label("Resolution note: " + issue.resolutionNote());
            note.setWrapText(true);
            content.getChildren().add(note);
        }
        var actions = new HBox(8);
        var dialog = new Dialog<Void>();
        dialog.setTitle("Review quality issue");
        if (issue.status() == QualityIssueStatus.OPEN || issue.status() == QualityIssueStatus.DEFERRED) {
            if (issue.responseId() != null && issue.questionId() != null) {
                var correct = new Button("Correct");
                correct.setOnAction(event -> { dialog.close(); correct(issue); });
                actions.getChildren().add(correct);
            }
            if (issue.responseId() != null) {
                var exclude = new Button("Exclude response");
                exclude.setOnAction(event -> { dialog.close(); single(issue, "Exclude response",
                        "Why is this response being excluded?", this::excludeCommand); });
                actions.getChildren().add(exclude);
            }
            var accept = new Button("Accept");
            accept.setOnAction(event -> { dialog.close(); single(issue, "Accept issue",
                    "Why should this issue be accepted?", ReviewCommand.Accept::new); });
            var defer = new Button("Defer");
            defer.setOnAction(event -> { dialog.close(); single(issue, "Defer issue",
                    "Why is this issue being deferred?", ReviewCommand.Defer::new); });
            actions.getChildren().addAll(accept, defer);
        }
        content.getChildren().add(actions);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(content);
        dialog.showAndWait();
    }

    private ReviewCommand excludeCommand(java.util.UUID issueId, String reason) {
        var issue = table.getItems().stream().filter(value -> value.id().equals(issueId)).findFirst().orElse(null);
        var responseId = issue != null ? issue.responseId()
                : table.getSelectionModel().getSelectedItems().stream().filter(value -> value.id().equals(issueId))
                        .map(QualityIssue::responseId).findFirst().orElse(null);
        return new ReviewCommand.Exclude(issueId, study.id(), responseId, reason);
    }

    private void correct(QualityIssue issue) {
        async.run(() -> {
            var page = datasets.query(study.id(), new DatasetQuery(null, "", null, null, "", DatasetSort.NEWEST, null, 0, 1));
            var row = datasets.detail(study.id(), issue.responseId());
            var variable = page.variables().stream().filter(value -> value.questionId().equals(issue.questionId()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("The variable no longer exists."));
            return new Object[]{variable, row.cell(issue.questionId()).displayValue()};
        }, result -> {
            var variable = (researchflow.domain.DatasetVariable) result[0];
            var currentValue = (String) result[1];
            CorrectionEditorDialog.create(variable, currentValue).showAndWait().ifPresent(values -> {
                var confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Apply this correction and resolve the issue?", ButtonType.CANCEL, ButtonType.OK);
                confirm.setHeaderText("Confirm controlled correction");
                if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
                apply(new ReviewCommand.Correct(issue.id(), study.id(), issue.responseId(), issue.questionId(),
                        values.rawValue(), values.reason()));
            });
        }, errors);
    }

    private void single(QualityIssue issue, String title, String prompt,
                        java.util.function.BiFunction<java.util.UUID, String, ReviewCommand> factory) {
        ReasonDialog.create(title, prompt, "Reason").showAndWait().ifPresent(reason -> {
            var confirm = new Alert(Alert.AlertType.CONFIRMATION, title + "?", ButtonType.CANCEL, ButtonType.OK);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            apply(factory.apply(issue.id(), reason));
        });
    }

    private void bulk(String title, String header, String prompt,
                      java.util.function.BiFunction<java.util.UUID, String, ReviewCommand> factory) {
        var selected = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;
        ReasonDialog.create(title + " " + selected.size() + " issue(s)", header, prompt).showAndWait().ifPresent(reason -> {
            var confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    title + " " + selected.size() + " selected issue(s)? This cannot be undone automatically.",
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.setHeaderText("Confirm bulk action");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            var commands = selected.stream().map(issue -> factory.apply(issue.id(), reason)).toList();
            table.setDisable(true);
            async.run(() -> review.applyAll(commands), ignored -> refresh(), failure -> { table.setDisable(false); handle(failure); });
        });
    }

    private void apply(ReviewCommand command) {
        table.setDisable(true);
        async.run(() -> review.apply(command), ignored -> refresh(), failure -> { table.setDisable(false); handle(failure); });
    }

    private void handle(Throwable failure) {
        if (failure instanceof ValidationException issue) {
            var alert = new Alert(Alert.AlertType.ERROR, String.join(" ", issue.errors().values()), ButtonType.OK);
            alert.setHeaderText("The action was not applied");
            alert.showAndWait();
        } else {
            errors.accept(failure);
        }
    }
}

