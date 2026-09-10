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

/** The reviewable issue queue: deterministic scan results, filters, and review actions. */
public final class QualityIssuesView {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final VBox root = new VBox(12);
    private final javafx.scene.control.ScrollPane viewport = new javafx.scene.control.ScrollPane(root);
    private final TableView<QualityIssue> table = new TableView<>();
    private final ComboBox<QualityIssueStatus> statusFilter = new ComboBox<>();
    private final Label status = new Label();
    private final BusyState scanBusy = new BusyState();
    private final ComboBox<researchflow.domain.QualityIssueType> typeFilter = new ComboBox<>();
    private final ComboBox<researchflow.domain.QualitySeverity> severityFilter = new ComboBox<>();
    private final javafx.scene.control.TextField searchFilter = new javafx.scene.control.TextField();
    private List<QualityIssue> loadedIssues = List.of();
    private boolean updatingFilter;
    private final VBox selectionDetails = new VBox(6);
    private final Study study;
    private final QualityService quality;
    private final QualityReviewService review;
    private final DatasetService datasets;
    private final Async async;

    public QualityIssuesView(Study study, QualityService quality, QualityReviewService review,
                             DatasetService datasets, Async async, Consumer<Throwable> errors) {
        this.study = study;
        this.quality = quality;
        this.review = review;
        this.datasets = datasets;
        this.async = async;
        root.setPadding(new Insets(20));
        viewport.setFitToWidth(true); viewport.setFitToHeight(true);

        var eyebrow = new Label("QUALITY REVIEW");
        eyebrow.getStyleClass().add("eyebrow");
        var title = new Label("Quality issues");
        title.getStyleClass().add("page-title");

        statusFilter.getItems().add(null);
        statusFilter.getItems().addAll(QualityIssueStatus.values());
        statusFilter.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(QualityIssueStatus value) { return value == null ? "All statuses" : value.toString(); }
            @Override public QualityIssueStatus fromString(String value) { return QualityIssueStatus.valueOf(value); }
        });
        statusFilter.setValue(QualityIssueStatus.OPEN);
        statusFilter.setPromptText("All statuses");
        statusFilter.valueProperty().addListener((observable, before, after) -> { if (!updatingFilter) filterIssues(); });
        var scan = new Button("Scan for quality issues");
        scan.getStyleClass().add("primary-button");
        scan.setOnAction(event -> scan());
        var reload = new Button("Refresh");
        reload.setOnAction(event -> refresh());
        scan.setDisable(study.status() == researchflow.domain.StudyStatus.ARCHIVED);
        var toolbar = new javafx.scene.layout.FlowPane(10, 8, new Label("Status"), statusFilter, scan, reload, status);

        enumFilter(typeFilter, researchflow.domain.QualityIssueType.values(), "All types");
        enumFilter(severityFilter, researchflow.domain.QualitySeverity.values(), "All severities");
        typeFilter.setId("quality-type-filter"); severityFilter.setId("quality-severity-filter");
        statusFilter.setId("quality-status-filter"); searchFilter.setId("quality-search-filter");
        searchFilter.setPromptText("Search explanation, response ID, question ID, or note");
        searchFilter.setPrefColumnCount(26);
        searchFilter.textProperty().addListener((observable, before, after) -> { if (!updatingFilter) filterIssues(); });
        var clear = new Button("Clear filters");
        clear.setOnAction(event -> {
            updatingFilter = true;
            statusFilter.setValue(null); typeFilter.setValue(null); severityFilter.setValue(null); searchFilter.clear();
            updatingFilter = false; filterIssues();
        });
        var filters = new javafx.scene.layout.FlowPane(10, 8, new Label("Type"), typeFilter,
                new Label("Severity"), severityFilter, searchFilter, clear);
        filters.getStyleClass().add("filter-panel");
        selectionDetails.getStyleClass().add("selection-panel");
        table.setPlaceholder(new Label("No issues match these filters. Clear filters or run a scan."));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setId("quality-issues-table");
        table.setRowFactory(ignored -> {
            var row = new javafx.scene.control.TableRow<QualityIssue>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == javafx.scene.input.MouseButton.PRIMARY
                        && event.getClickCount() == 2) review(row.getItem());
            });
            return row;
        });
        table.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER
                    && table.getSelectionModel().getSelectedItems().size() == 1) {
                review(table.getSelectionModel().getSelectedItem()); event.consume();
            }
        });
        table.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<QualityIssue>) change -> showSelection());
        buildColumns();

        var reviewButton = new Button("Review selected");
        reviewButton.disableProperty().bind(Bindings.size(table.getSelectionModel().getSelectedItems()).isNotEqualTo(1));
        reviewButton.setOnAction(event -> review(table.getSelectionModel().getSelectedItem()));
        var acceptAll = new Button("Accept selected");
        var deferAll = new Button("Defer selected");
        var excludeAll = new Button("Exclude selected");
        for (var button : List.of(acceptAll, deferAll, excludeAll)) {
            button.disableProperty().bind(Bindings.createBooleanBinding(
                    () -> study.status() == researchflow.domain.StudyStatus.ARCHIVED
                            || table.getSelectionModel().getSelectedItems().isEmpty()
                            || table.getSelectionModel().getSelectedItems().stream().anyMatch(issue ->
                            (issue.status() != QualityIssueStatus.OPEN && issue.status() != QualityIssueStatus.DEFERRED)
                                    || (button == excludeAll && issue.responseId() == null)),
                    table.getSelectionModel().getSelectedItems()));
        }
        acceptAll.setOnAction(event -> bulk("Accept", "Retain the selected data as-is.",
                "Why should these issues be accepted?", ReviewCommand.Accept::new));
        deferAll.setOnAction(event -> bulk("Defer", "Postpone review of the selected issues.",
                "Why are these issues being deferred?", ReviewCommand.Defer::new));
        excludeAll.setOnAction(event -> bulk("Exclude", "Exclude the affected responses from the dataset.",
                "Why are these responses being excluded?", this::excludeCommand));
        var actions = new javafx.scene.layout.FlowPane(8, 8, reviewButton, acceptAll, deferAll, excludeAll);
        var instructions = new Label("Click a row to see its details and actions below. Double-click or press Enter to open review. Use Ctrl/Shift to select several issues.");
        instructions.setWrapText(true);
        selectionDetails.setId("quality-selection-details");
        showSelection();

        var detailsScroll = new javafx.scene.control.ScrollPane(selectionDetails);
        detailsScroll.setFitToWidth(true); detailsScroll.setPrefHeight(168); detailsScroll.setMinHeight(110);
        root.getChildren().addAll(eyebrow, title, toolbar, scanBusy.node(), filters, instructions, table, detailsScroll, actions);
        table.setMinHeight(140);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        refresh();
    }

    public Parent node() {
        return viewport;
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
        var state = new TableColumn<QualityIssue, String>("Status");
        state.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().status().toString()));
        table.getColumns().setAll(List.of(type, severity, state, responseId, explanation, created));
    }

    private void refresh() {
        root.setDisable(true);
        async.run(() -> quality.list(study.id(), null), issues -> {
            root.setDisable(false);
            loadedIssues = List.copyOf(issues);
            filterIssues();
        }, failure -> { root.setDisable(false); handle(failure); });
    }

    private void scan() {
        setScanning(true);
        var task = async.run(() -> quality.scan(study.id()), issues -> {
            setScanning(false); scanBusy.finish();
            loadedIssues = List.copyOf(issues);
            filterIssues();
            status.setText("Scan complete. " + status.getText());
        }, failure -> { setScanning(false); scanBusy.finish(); handle(failure); });
        scanBusy.start(task, () -> {
            setScanning(false);
            status.setText("Scan cancelled. Refresh to see any changes saved before cancellation.");
        });
    }

    private void setScanning(boolean value) {
        for (var child : root.getChildren()) if (child != scanBusy.node()) child.setDisable(value);
    }

    private <T extends Enum<T>> void enumFilter(ComboBox<T> box, T[] values, String all) {
        box.getItems().add(null); box.getItems().addAll(values); box.setPromptText(all);
        box.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(T value) { return value == null ? all : value.toString(); }
            @Override public T fromString(String text) { throw new UnsupportedOperationException(); }
        });
        box.valueProperty().addListener((observable, before, after) -> { if (!updatingFilter) filterIssues(); });
    }

    private void filterIssues() {
        var filter = new researchflow.quality.QualityIssueFilter(statusFilter.getValue(), typeFilter.getValue(),
                severityFilter.getValue(), searchFilter.getText());
        table.getSelectionModel().clearSelection();
        table.getItems().setAll(loadedIssues.stream().filter(filter::matches).toList());
        status.setText(table.getItems().size() + " of " + loadedIssues.size() + " issue(s)");
    }

    private boolean canReview(QualityIssue issue) {
        return study.status() != researchflow.domain.StudyStatus.ARCHIVED
                && (issue.status() == QualityIssueStatus.OPEN || issue.status() == QualityIssueStatus.DEFERRED);
    }

    private void showSelection() {
        selectionDetails.getChildren().clear();
        var selected = table.getSelectionModel().getSelectedItems();
        if (selected.size() != 1) {
            selectionDetails.getChildren().add(new Label(selected.isEmpty()
                    ? "Select an issue to inspect it." : selected.size() + " issues selected. Use the bulk actions below."));
            return;
        }
        var issue = selected.getFirst();
        selectionDetails.getChildren().add(issueSummary(issue));
        var actions = new javafx.scene.layout.FlowPane(8, 8);
        if (issue.responseId() != null) {
            var inspect = new Button("View response");
            inspect.setOnAction(event -> inspectResponse(issue)); actions.getChildren().add(inspect);
        }
        if (canReview(issue)) {
            if (issue.responseId() != null && issue.questionId() != null) {
                var correct = new Button("Correct answer"); correct.setId("quality-correct-answer");
                correct.setOnAction(event -> correct(issue)); actions.getChildren().add(correct);
            }
            var reviewSelected = new Button("Review this issue");
            reviewSelected.setOnAction(event -> review(issue)); actions.getChildren().add(reviewSelected);
        }
        selectionDetails.getChildren().add(0, actions);
    }

    private VBox issueSummary(QualityIssue issue) {
        var explanation = new Label(issue.explanation()); explanation.setWrapText(true);
        var summary = new VBox(6, new Label(issue.type() + " / " + issue.severity() + " / " + issue.status()), explanation);
        var target = new Label("Response: " + (issue.responseId() == null ? "Whole dataset" : issue.responseId())
                + "\nQuestion: " + (issue.questionId() == null ? "Whole response / dataset" : issue.questionId()));
        target.setWrapText(true); summary.getChildren().add(target);
        if (issue.resolutionNote() != null && !issue.resolutionNote().isBlank()) {
            var note = new Label("Review note: " + issue.resolutionNote()); note.setWrapText(true); summary.getChildren().add(note);
        }
        var help = new Label(canReview(issue)
                ? "Correct edits one answer. Accept keeps the data as-is. Defer postpones review (find it under DEFERRED). Exclude removes the whole response from analysis without deleting it. Every action needs a reason."
                : "Read-only: this issue is already reviewed or the study is archived. Accepted data is retained; resolved issues stay in the review history.");
        help.setWrapText(true); summary.getChildren().add(help);
        for (var child : summary.getChildren()) if (child instanceof Label label) label.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        return summary;
    }

    private void review(QualityIssue issue) {
        if (issue == null) return;
        var dialog = new Dialog<ButtonType>();
        dialog.setTitle("Review quality issue");
        own(dialog);
        var correct = new ButtonType("Correct answer", javafx.scene.control.ButtonBar.ButtonData.OTHER);
        var exclude = new ButtonType("Exclude response", javafx.scene.control.ButtonBar.ButtonData.OTHER);
        var accept = new ButtonType("Accept", javafx.scene.control.ButtonBar.ButtonData.OTHER);
        var defer = new ButtonType("Defer", javafx.scene.control.ButtonBar.ButtonData.OTHER);
        if (canReview(issue)) {
            if (issue.responseId() != null && issue.questionId() != null) dialog.getDialogPane().getButtonTypes().add(correct);
            if (issue.responseId() != null) dialog.getDialogPane().getButtonTypes().add(exclude);
            dialog.getDialogPane().getButtonTypes().addAll(accept, defer);
        }
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        var content = issueSummary(issue); content.setPadding(new Insets(12));
        var scroll = new javafx.scene.control.ScrollPane(content); scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(600); scroll.setPrefViewportHeight(300);
        dialog.getDialogPane().setContent(scroll); dialog.setResizable(true);
        dialog.setResultConverter(button -> button);
        // Dispatch only after the review window has closed, avoiding nested modal action handlers.
        var action = dialog.showAndWait().orElse(ButtonType.CLOSE);
        if (action == correct) correct(issue);
        else if (action == exclude) single(issue, "Exclude response", "Why is this response being excluded?",
                (id, reason) -> new ReviewCommand.Exclude(id, study.id(), issue.responseId(), reason));
        else if (action == accept) single(issue, "Accept issue", "Why should this issue be accepted?", ReviewCommand.Accept::new);
        else if (action == defer) single(issue, "Defer issue", "Why is this issue being deferred?", ReviewCommand.Defer::new);
    }

    private <T> Dialog<T> own(Dialog<T> dialog) {
        if (root.getScene() != null && root.getScene().getWindow() != null) dialog.initOwner(root.getScene().getWindow());
        return dialog;
    }

    private ReviewCommand excludeCommand(java.util.UUID issueId, String reason) {
        var issue = table.getItems().stream().filter(value -> value.id().equals(issueId)).findFirst().orElse(null);
        var responseId = issue != null ? issue.responseId()
                : table.getSelectionModel().getSelectedItems().stream().filter(value -> value.id().equals(issueId))
                        .map(QualityIssue::responseId).findFirst().orElse(null);
        return new ReviewCommand.Exclude(issueId, study.id(), responseId, reason);
    }

    private void inspectResponse(QualityIssue issue) {
        root.setDisable(true);
        status.setText("Loading response...");
        async.run(() -> {
            var row = datasets.detail(study.id(), issue.responseId());
            var page = datasets.query(study.id(), new DatasetQuery(row.formId(), "", null, null, "", DatasetSort.NEWEST, null, 0, 1));
            var lines = new java.util.ArrayList<String>();
            lines.add(row.formTitle() + " / " + row.status());
            lines.add("Response: " + row.responseId());
            for (var variable : page.variables()) {
                var cell = row.cell(variable.questionId());
                lines.add(variable.label() + ": " + (cell.missing() ? "(missing)" : cell.displayValue()));
            }
            return lines;
        }, lines -> {
            root.setDisable(false); status.setText("Response loaded");
            var content = new VBox(10); content.setPadding(new Insets(12));
            for (var line : lines) { var label = new Label(line); label.setWrapText(true); content.getChildren().add(label); }
            var scroll = new javafx.scene.control.ScrollPane(content); scroll.setFitToWidth(true);
            scroll.setPrefViewportWidth(600); scroll.setPrefViewportHeight(400);
            var dialog = new Dialog<Void>(); dialog.setTitle("Response details"); dialog.setResizable(true);
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE); dialog.getDialogPane().setContent(scroll);
            own(dialog).showAndWait();
        }, failure -> { root.setDisable(false); handle(failure); });
    }

    private void correct(QualityIssue issue) {
        root.setDisable(true);
        status.setText("Loading answer...");
        async.run(() -> {
            var page = datasets.query(study.id(), new DatasetQuery(null, "", null, null, "", DatasetSort.NEWEST, null, 0, 1));
            var row = datasets.detail(study.id(), issue.responseId());
            var variable = page.variables().stream().filter(value -> value.questionId().equals(issue.questionId()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("The variable no longer exists."));
            var cell = row.cell(issue.questionId());
            return new Object[]{variable, cell.missing() ? "" : cell.displayValue()};
        }, result -> {
            root.setDisable(false);
            status.setText("Answer loaded");
            var variable = (researchflow.domain.DatasetVariable) result[0];
            var currentValue = (String) result[1];
            own(CorrectionEditorDialog.create(variable, currentValue)).showAndWait().ifPresent(values -> {
                var confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Apply this correction and resolve the issue?", ButtonType.CANCEL, ButtonType.OK);
                confirm.setHeaderText("Confirm controlled correction");
                if (own(confirm).showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
                apply(new ReviewCommand.Correct(issue.id(), study.id(), issue.responseId(), issue.questionId(),
                        values.rawValue(), values.reason()));
            });
        }, failure -> { root.setDisable(false); handle(failure); });
    }

    private void single(QualityIssue issue, String title, String prompt,
                        java.util.function.BiFunction<java.util.UUID, String, ReviewCommand> factory) {
        own(ReasonDialog.create(title, prompt, "Reason")).showAndWait().ifPresent(reason -> {
            var confirm = new Alert(Alert.AlertType.CONFIRMATION, title + "?", ButtonType.CANCEL, ButtonType.OK);
            if (own(confirm).showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            apply(factory.apply(issue.id(), reason));
        });
    }

    private void bulk(String title, String header, String prompt,
                      java.util.function.BiFunction<java.util.UUID, String, ReviewCommand> factory) {
        var selected = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) return;
        own(ReasonDialog.create(title + " " + selected.size() + " issue(s)", header, prompt)).showAndWait().ifPresent(reason -> {
            var confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    title + " " + selected.size() + " selected issue(s)? This cannot be undone automatically.",
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.setHeaderText("Confirm bulk action");
            if (own(confirm).showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            var commands = selected.stream().map(issue -> factory.apply(issue.id(), reason)).toList();
            root.setDisable(true);
            async.run(() -> review.applyAll(commands), ignored -> refresh(), failure -> { refresh(); handle(failure, "Bulk action stopped. Earlier successful changes were saved; review the refreshed list before retrying."); });
        });
    }

    private void apply(ReviewCommand command) {
        root.setDisable(true);
        async.run(() -> review.apply(command), ignored -> refresh(), failure -> { root.setDisable(false); handle(failure); });
    }

    private void handle(Throwable failure) {
        handle(failure, "Quality operation could not be completed");
    }

    private void handle(Throwable failure, String header) {
        var message = failure instanceof ValidationException invalid
                ? String.join(" ", invalid.errors().values()) : failure.getMessage();
        var alert = new Alert(Alert.AlertType.ERROR, message == null
                ? "Refresh the issue list and try again." : message, ButtonType.OK);
        alert.setHeaderText(header);
        own(alert).showAndWait();
    }
}
