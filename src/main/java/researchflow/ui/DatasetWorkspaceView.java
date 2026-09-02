package researchflow.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import researchflow.domain.DatasetFilterOperator;
import researchflow.domain.DatasetPage;
import researchflow.domain.DatasetQuery;
import researchflow.domain.DatasetRow;
import researchflow.domain.DatasetSort;
import researchflow.domain.DatasetVariable;
import researchflow.domain.Study;
import researchflow.service.AuditService;
import researchflow.service.DatasetCorrectionService;
import researchflow.service.DatasetService;
import researchflow.service.ValidationException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public final class DatasetWorkspaceView {
    private final VBox dataset = new VBox(12);
    private final TableView<DatasetRow> table = new TableView<>();
    private final TextField search = new TextField();
    private final ComboBox<DatasetVariable> filterVariable = new ComboBox<>();
    private final ComboBox<DatasetFilterOperator> filterOperator = new ComboBox<>();
    private final TextField filterValue = new TextField();
    private final ComboBox<DatasetSort> sort = new ComboBox<>();
    private final ComboBox<DatasetVariable> sortVariable = new ComboBox<>();
    private final Label pageStatus = new Label();
    private final Label validation = new Label();
    private final Button previous = new Button("Previous");
    private final Button next = new Button("Next");
    private final DatasetService datasets;
    private final DatasetCorrectionService corrections;
    private final Async async;
    private final Study study;
    private final Consumer<Throwable> errors;
    private DatasetPage current;
    private int offset;
    private static final int PAGE_SIZE = 50;

    public DatasetWorkspaceView(Study study, DatasetService datasets, DatasetCorrectionService corrections,
                                AuditService audits, Async async, Consumer<Throwable> errors) {
        this.study = study; this.datasets = datasets; this.corrections = corrections; this.async = async; this.errors = errors;
        dataset.setPadding(new Insets(20));
        var eyebrow = new Label("DATASET / RESEARCHER MODE"); eyebrow.getStyleClass().add("eyebrow");
        var title = new Label("Dataset"); title.getStyleClass().add("page-title");
        search.setPromptText("Search responses and values");
        filterVariable.setPromptText("Filter variable"); filterVariable.setPrefWidth(210);
        filterOperator.getItems().setAll(DatasetFilterOperator.values()); filterOperator.setPromptText("Operator");
        filterValue.setPromptText("Filter value");
        sort.getItems().setAll(DatasetSort.values()); sort.setValue(DatasetSort.NEWEST);
        sortVariable.setPromptText("Sort variable"); sortVariable.setPrefWidth(190);
        var apply = new Button("Apply"); apply.getStyleClass().add("primary-button");
        var clear = new Button("Clear");
        apply.setOnAction(event -> { offset = 0; refresh(); });
        clear.setOnAction(event -> { search.clear(); filterVariable.setValue(null); filterOperator.setValue(null);
            filterValue.clear(); sort.setValue(DatasetSort.NEWEST); sortVariable.setValue(null); offset = 0; refresh(); });
        filterOperator.valueProperty().addListener((obs, old, value) -> filterValue.setDisable(value == DatasetFilterOperator.IS_MISSING));
        var filters = new HBox(8, search, filterVariable, filterOperator, filterValue, sort, sortVariable, apply, clear);
        table.setPlaceholder(new Label("No responses match the current query."));
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) showDetails(table.getSelectionModel().getSelectedItem()); });
        var details = new Button("Response details"); details.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        details.setOnAction(event -> showDetails(table.getSelectionModel().getSelectedItem()));
        previous.setOnAction(event -> { offset = Math.max(0, offset - PAGE_SIZE); refresh(); });
        next.setOnAction(event -> { offset += PAGE_SIZE; refresh(); });
        validation.getStyleClass().add("field-error");
        var paging = new HBox(8, previous, next, pageStatus, details);
        dataset.getChildren().addAll(eyebrow, title, filters, validation, table, paging);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        var tabs = new TabPane(new Tab("Dataset", dataset),
                new Tab("Audit timeline", new AuditTimelineView(study, audits, async, errors).node()));
        tabs.getTabs().forEach(tab -> tab.setClosable(false));
        refresh();
        this.root = tabs;
    }

    private final Parent root;
    public Parent node() { return root; }

    private void refresh() {
        validation.setText(""); table.setDisable(true);
        var query = new DatasetQuery(null, search.getText(), questionId(filterVariable.getValue()),
                filterOperator.getValue(), filterValue.getText(), sort.getValue(), questionId(sortVariable.getValue()),
                offset, PAGE_SIZE);
        async.run(() -> datasets.query(study.id(), query), this::showPage, failure -> {
            table.setDisable(false);
            if (failure instanceof ValidationException issue) validation.setText(String.join(" ", issue.errors().values()));
            else errors.accept(failure);
        });
    }

    private void showPage(DatasetPage page) {
        current = page; table.setDisable(false); table.getItems().setAll(page.rows());
        var selectedFilter = questionId(filterVariable.getValue());
        var selectedSort = questionId(sortVariable.getValue());
        filterVariable.getItems().setAll(page.variables()); sortVariable.getItems().setAll(page.variables());
        if (selectedFilter != null) page.variables().stream().filter(value -> value.questionId().equals(selectedFilter))
                .findFirst().ifPresent(filterVariable::setValue);
        if (selectedSort != null) page.variables().stream().filter(value -> value.questionId().equals(selectedSort))
                .findFirst().ifPresent(sortVariable::setValue);
        buildColumns(page);
        var first = page.totalRows() == 0 ? 0 : page.offset() + 1;
        var last = Math.min(page.totalRows(), (long) page.offset() + page.rows().size());
        pageStatus.setText(first + "–" + last + " of " + page.totalRows());
        previous.setDisable(page.offset() == 0); next.setDisable(page.offset() + page.limit() >= page.totalRows());
    }

    private void buildColumns(DatasetPage page) {
        table.getColumns().clear();
        var id = new TableColumn<DatasetRow, String>("Response");
        id.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().responseId().toString().substring(0, 8)));
        var form = new TableColumn<DatasetRow, String>("Form");
        form.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().formTitle()));
        var submitted = new TableColumn<DatasetRow, String>("Submitted");
        var formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
        submitted.setCellValueFactory(row -> new SimpleStringProperty(formatter.format(row.getValue().submittedAt())));
        table.getColumns().addAll(id, form, submitted);
        for (var variable : page.variables()) {
            var column = new TableColumn<DatasetRow, String>(variable.variableKey());
            var header = new Label(variable.variableKey());
            header.setTooltip(new Tooltip(variable.formTitle() + " · " + variable.label() + " · " + variable.type()));
            column.setText(null); column.setGraphic(header);
            column.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().formId().equals(variable.formId())
                    ? row.getValue().cell(variable.questionId()).displayValue() : ""));
            column.setPrefWidth(145); table.getColumns().add(column);
        }
    }

    private void showDetails(DatasetRow row) {
        if (row == null || current == null) return;
        var content = new VBox(9); content.setPadding(new Insets(8));
        content.getChildren().add(new Label("Response " + row.responseId()));
        for (var variable : current.variables().stream().filter(value -> value.formId().equals(row.formId())).toList()) {
            var cell = row.cell(variable.questionId());
            var label = new Label(variable.label() + ": " + cell.displayValue()); label.setWrapText(true);
            var correct = new Button("Correct");
            correct.setOnAction(event -> correct(row, variable, cell.displayValue()));
            content.getChildren().add(new HBox(10, label, correct));
        }
        var dialog = new Dialog<Void>(); dialog.setTitle("Response details");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        var scroll = new javafx.scene.control.ScrollPane(content); scroll.setFitToWidth(true);
        scroll.setPrefViewportWidth(650); scroll.setPrefViewportHeight(520);
        dialog.getDialogPane().setContent(scroll); dialog.showAndWait();
    }

    private void correct(DatasetRow row, DatasetVariable variable, String currentValue) {
        CorrectionEditorDialog.create(variable, currentValue).showAndWait().ifPresent(values -> {
            var confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Apply this correction? The prior value and reason will remain in correction history.",
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.setHeaderText("Confirm controlled correction");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            async.run(() -> corrections.correct(study.id(), row.responseId(), variable.questionId(),
                    values.rawValue(), values.reason()), () -> refresh(), failure -> {
                if (failure instanceof ValidationException issue) {
                    var alert = new Alert(Alert.AlertType.ERROR, String.join(" ", issue.errors().values()), ButtonType.OK);
                    alert.setHeaderText("Correction was not applied"); alert.showAndWait();
                } else errors.accept(failure);
            });
        });
    }

    private static UUID questionId(DatasetVariable variable) { return variable == null ? null : variable.questionId(); }
}
