package researchflow.ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import researchflow.dataimport.ImportColumnPlan;
import researchflow.dataimport.ImportPreview;
import researchflow.dataimport.ImportResult;
import researchflow.domain.QuestionType;
import researchflow.domain.Study;
import researchflow.service.DatasetImportService;
import researchflow.service.ValidationException;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Imports an external dataset file into this Study as a new Form and Responses. Reuses the existing
 * collection pipeline, so the result is immediately usable from Dataset, Quality, Versions, and
 * Analysis with no separate code path.
 */
public final class ImportWorkspaceView {
    private final VBox root = new VBox(16);
    private final TextField title = new TextField();
    private final Label fileLabel = new Label("No file selected.");
    private final VBox columnsBox = new VBox(6);
    private final Label status = new Label();
    private final Button importButton = new Button("Import");
    private final Study study;
    private final DatasetImportService service;
    private final Async async;
    private final Runnable onImported;
    private final Consumer<Throwable> errors;
    private final List<ColumnRow> columnRows = new ArrayList<>();
    private final Button choose = new Button("Choose dataset file...");
    private final ComboBox<String> worksheet = new ComboBox<>();
    private boolean loadingSheets;
    private Path selectedFile;
    private ImportPreview preview;
    private researchflow.service.CancellationToken cancellation;

    public ImportWorkspaceView(Study study, DatasetImportService service, Async async, Runnable onImported,
                               Consumer<Throwable> errors) {
        this.study = study;
        this.service = service;
        this.async = async;
        this.onImported = onImported;
        this.errors = errors;
        root.setPadding(new Insets(20));

        var eyebrow = new Label("IMPORT / RESEARCHER MODE");
        eyebrow.getStyleClass().add("eyebrow");
        var heading = new Label("Import a dataset");
        heading.getStyleClass().add("page-title");
        var hint = new Label("Import CSV, TSV, JSON, or Excel (.xlsx/.xls) as a new form and set of responses, ready for the "
                + "Dataset, Quality, and Analysis workspaces.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        choose.setOnAction(event -> chooseFile());
        var fileRow = new HBox(10, choose, fileLabel);
        title.setPromptText("Form title for the imported data");

        var form = new GridPane();
        form.getStyleClass().add("content-panel");
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("File"), fileRow);
        form.addRow(1, new Label("Form title"), title);
        worksheet.setPromptText("Excel worksheet"); worksheet.setDisable(true);
        worksheet.setOnAction(event -> { if (!loadingSheets && worksheet.getValue() != null) loadPreview(); });
        form.addRow(2, new Label("Worksheet (Excel)"), worksheet);
        var formatHelp = new Label("CSV/TSV: first row is headers. Excel: select one worksheet; first non-empty row is headers. JSON: array of flat row objects, or {\"data\": [...]}. Excel formulas use saved results; save the workbook before importing.");
        formatHelp.setWrapText(true); form.add(formatHelp, 0, 3, 2, 1);

        status.getStyleClass().add("field-error");
        status.setWrapText(true);
        importButton.getStyleClass().add("primary-button");
        importButton.setDisable(true);
        importButton.setOnAction(event -> runImport());

        var cancel = new Button("Cancel import");
        cancel.setOnAction(event -> { if (cancellation != null) { cancellation.cancel(); status.setText("Cancellation requested; waiting for the current row to finish."); } });
        var columnsTitle = new Label("Columns");
        columnsTitle.getStyleClass().add("section-title");
        columnsBox.getChildren().setAll(Visuals.emptyState("Import", "Bring your data into focus",
                "Choose a file above to preview and map its columns."));
        var columnsScroll = new ScrollPane(columnsBox);
        columnsScroll.setFitToWidth(true);

        root.getChildren().addAll(eyebrow, heading, hint, form, status, columnsTitle, columnsScroll, new HBox(8, importButton, cancel));
        VBox.setVgrow(columnsScroll, Priority.ALWAYS);
    }

    public Parent node() {
        return root;
    }

    private void chooseFile() {
        var chooser = new FileChooser();
        chooser.setTitle("Choose a dataset file");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Dataset files", "*.csv", "*.tsv", "*.json", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("Excel workbooks", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("CSV / TSV", "*.csv", "*.tsv"),
                new FileChooser.ExtensionFilter("JSON", "*.json"));
        var window = root.getScene() == null ? null : root.getScene().getWindow();
        var file = chooser.showOpenDialog(window);
        if (file == null) return;

        selectedFile = file.toPath();
        fileLabel.setText(file.getName());
        if (title.getText().isBlank()) {
            var name = file.getName();
            var dot = name.lastIndexOf('.');
            title.setText(dot > 0 ? name.substring(0, dot) : name);
        }
        preview = null; columnRows.clear();
        loadingSheets = true; worksheet.getItems().clear(); loadingSheets = false;
        worksheet.setDisable(true);
        if (researchflow.dataimport.DatasetFileReader.isExcel(selectedFile)) {
            choose.setDisable(true); importButton.setDisable(true);
            var filePath = selectedFile;
            async.run(() -> service.worksheets(filePath), names -> {
                loadingSheets = true;
                worksheet.getItems().setAll(names); worksheet.setValue(names.getFirst());
                loadingSheets = false; loadPreview();
            }, this::previewFailed);
        } else loadPreview();
    }

    private void loadPreview() {
        preview = null; columnRows.clear();
        status.setText("");
        columnsBox.getChildren().setAll(new Label("Loading preview..."));
        importButton.setDisable(true); choose.setDisable(true); worksheet.setDisable(true);
        var filePath = selectedFile; var sheet = worksheet.getValue();
        async.run(() -> service.preview(filePath, sheet), this::showPreview, this::previewFailed);
    }

    private void previewFailed(Throwable failure) {
        preview = null; choose.setDisable(false); importButton.setDisable(true);
        worksheet.setDisable(worksheet.getItems().isEmpty());
        columnsBox.getChildren().setAll(new Label("Choose a file or another worksheet to see its columns."));
        status.setText(failure.getMessage());
    }

    private void showPreview(ImportPreview loaded) {
        choose.setDisable(false); worksheet.setDisable(worksheet.getItems().isEmpty());
        preview = loaded;
        columnRows.clear();
        columnsBox.getChildren().clear();
        var summary = new Label(loaded.document().rows().size() + " data row(s) detected.");
        summary.getStyleClass().add("muted");
        columnsBox.getChildren().add(summary);
        var header = new HBox(10, width(new Label(""), 24), width(new Label("Column"), 140),
                width(new Label("Label"), 180), width(new Label("Type"), 110), new Label("Required"));
        header.getChildren().forEach(node -> node.getStyleClass().add("muted"));
        columnsBox.getChildren().add(header);
        for (var column : loaded.columns()) {
            var row = new ColumnRow(column);
            columnRows.add(row);
            columnsBox.getChildren().add(row.node());
        }
        importButton.setDisable(false);
    }

    private void runImport() {
        status.setText("");
        if (preview == null) {
            status.setText("Choose a dataset file first.");
            return;
        }
        var columns = columnRows.stream().map(ColumnRow::currentPlan).toList();
        importButton.setDisable(true);
        choose.setDisable(true); worksheet.setDisable(true); columnsBox.setDisable(true); title.setDisable(true);
        var formTitle = title.getText();
        var selectedPreview = preview;
        cancellation = new researchflow.service.CancellationToken();
        var token = cancellation;
        async.run(() -> service.importInto(study.id(), formTitle, selectedPreview, columns, token, count -> {
            if (count % 100 == 0 || count == selectedPreview.document().rows().size())
                async.update(() -> status.setText("Processed " + count + " of " + selectedPreview.document().rows().size() + " rows."));
        }), this::showResult, failure -> {
            finishImport();
            if (failure instanceof ValidationException issue) status.setText(String.join(" ", issue.errors().values()));
            else errors.accept(failure);
        });
    }

    private void finishImport() {
        cancellation = null;
        importButton.setDisable(false); choose.setDisable(false);
        worksheet.setDisable(worksheet.getItems().isEmpty()); columnsBox.setDisable(false); title.setDisable(false);
    }

    private void showResult(ImportResult result) {
        finishImport();
        var message = new StringBuilder(result.summary());
        if (result.skippedCount() > 0) message.append(' ').append(result.skippedCount()).append(" row(s) skipped.");

        var content = new VBox(8, new Label(message.toString()));
        if (!result.errors().isEmpty()) {
            var errorList = new VBox(2);
            for (var rowError : result.errors()) {
                var label = new Label("Row " + rowError.rowNumber() + ": " + rowError.message());
                label.getStyleClass().add("muted");
                label.setWrapText(true);
                errorList.getChildren().add(label);
            }
            var scroll = new ScrollPane(errorList);
            scroll.setFitToWidth(true);
            scroll.setPrefViewportHeight(160);
            content.getChildren().add(scroll);
        }
        var alert = new Alert(!result.completedNormally() || result.skippedCount() > 0 ? Alert.AlertType.WARNING : Alert.AlertType.INFORMATION);
        alert.setHeaderText(result.completedNormally() ? "Import finished" : "Import stopped with partial results");
        var saveDetails = new Button("Save import outcome and errors");
        saveDetails.setOnAction(event -> {
            var chooser = new FileChooser(); chooser.setInitialFileName("import-outcome.txt");
            var file = chooser.showSaveDialog(root.getScene().getWindow());
            if (file != null) async.run(() -> service.exportErrors(result, file.toPath()), () -> status.setText("Import outcome saved."), errors);
        });
        content.getChildren().add(saveDetails);
        alert.getDialogPane().setContent(content);
        alert.getButtonTypes().setAll(ButtonType.OK);
        alert.showAndWait();
        if (result.completedNormally()) onImported.run();
        else status.setText(result.summary());
    }

    private static Label width(Label label, double width) {
        label.setPrefWidth(width);
        return label;
    }

    private static final class ColumnRow {
        private final ImportColumnPlan original;
        private final CheckBox included = new CheckBox();
        private final TextField label;
        private final ComboBox<QuestionType> type = new ComboBox<>();
        private final CheckBox required = new CheckBox();
        private final HBox box;

        ColumnRow(ImportColumnPlan plan) {
            this.original = plan;
            included.setSelected(plan.included());
            var header = width(new Label(plan.header().isBlank() ? "(no header)" : plan.header()), 140);
            label = new TextField(plan.label());
            label.setPrefWidth(180);
            type.getItems().setAll(QuestionType.SHORT_TEXT, QuestionType.NUMBER, QuestionType.DATE);
            type.setValue(plan.type());
            required.setSelected(plan.required());
            box = new HBox(10, included, header, label, type, required);
            box.setPadding(new Insets(3, 0, 3, 0));
        }

        Parent node() {
            return box;
        }

        ImportColumnPlan currentPlan() {
            return new ImportColumnPlan(original.columnIndex(), original.header(), original.variableKey(),
                    label.getText(), type.getValue(), required.isSelected(), included.isSelected());
        }
    }
}
