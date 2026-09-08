package researchflow.ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import researchflow.domain.ReportDocument;
import researchflow.domain.Study;
import researchflow.service.ReportService;

import java.util.function.Consumer;

/** Composes the current report on demand and offers a self-contained HTML export. */
public final class ReportWorkspaceView {
    private final VBox root = new VBox(16);
    private final VBox preview = new VBox(10);
    private final Button exportButton = new Button("Export HTML…");
    private final BusyState exportBusy = new BusyState();
    private final Study study;
    private final ReportService service;
    private final Async async;
    private final Consumer<Throwable> errors;
    private boolean composed;

    public ReportWorkspaceView(Study study, ReportService service, Async async, Consumer<Throwable> errors) {
        this.study = study;
        this.service = service;
        this.async = async;
        this.errors = errors;
        root.setPadding(new Insets(20));

        var eyebrow = new Label("REPORT");
        eyebrow.getStyleClass().add("eyebrow");
        var title = new Label("Study report");
        title.getStyleClass().add("page-title");
        var hint = new Label("Composed fresh from the Study's current data, quality summary, and approved findings. "
                + "Unapproved findings are never included.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        var refresh = new Button("Preview");
        refresh.getStyleClass().add("primary-button");
        refresh.setOnAction(event -> refreshPreview());
        exportButton.setDisable(true);
        exportButton.setOnAction(event -> export());
        var toolbar = new javafx.scene.layout.HBox(8, refresh, exportButton, exportBusy.node());

        preview.getChildren().setAll(new Label("Select Preview to compose the report."));
        var scroll = new ScrollPane(preview);
        scroll.setFitToWidth(true);

        root.getChildren().addAll(eyebrow, title, hint, toolbar, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
    }

    public Parent node() {
        return root;
    }

    private void refreshPreview() {
        preview.getChildren().setAll(new Label("Composing…"));
        exportButton.setDisable(true);
        async.run(() -> service.compose(study.id()), this::showPreview, errors);
    }

    private void showPreview(ReportDocument document) {
        composed = true;
        exportButton.setDisable(false);
        preview.getChildren().clear();

        var heading = new Label(document.study().title());
        heading.getStyleClass().add("section-title");
        preview.getChildren().add(heading);
        if (!document.study().description().isBlank()) preview.getChildren().add(wrapped(document.study().description()));

        preview.getChildren().add(sectionLabel("Study summary"));
        preview.getChildren().add(new Label("Forms: " + document.formCount() + "   Responses: " + document.responseCount()));
        preview.getChildren().add(new Label("Active dataset version: " + (document.activeVersion() == null ? "none yet"
                : "v" + document.activeVersion().versionNumber() + " — " + document.activeVersion().reason())));

        var quality = document.qualitySummary();
        preview.getChildren().add(sectionLabel("Quality summary"));
        preview.getChildren().add(new Label("Open: " + quality.openIssues() + "   Accepted: " + quality.acceptedIssues()
                + "   Deferred: " + quality.deferredIssues() + "   Resolved: " + quality.resolvedIssues()));

        preview.getChildren().add(sectionLabel("Approved findings"));
        if (document.approvedFindings().isEmpty()) {
            preview.getChildren().add(new Label("No findings have been approved yet."));
        } else {
            for (var finding : document.approvedFindings()) {
                var box = new VBox(4, wrapped(finding.text()));
                if (finding.evidenceSummary() != null && !finding.evidenceSummary().isBlank()) {
                    box.getChildren().add(muted(finding.evidenceSummary()));
                }
                if (finding.chart() != null) box.getChildren().add(ChartView.render(finding.chart()));
                box.getStyleClass().add("question-card");
                preview.getChildren().add(box);
            }
        }

        preview.getChildren().add(sectionLabel("Limitations"));
        for (var limitation : document.limitations()) preview.getChildren().add(wrapped("• " + limitation));
    }

    private void export() {
        if (!composed) {
            refreshPreview();
            return;
        }
        var chooser = new FileChooser();
        chooser.setTitle("Export report as HTML");
        chooser.setInitialFileName(sanitizeFileName(study.title()) + "-report.html");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("HTML files", "*.html"));
        var window = root.getScene() == null ? null : root.getScene().getWindow();
        var file = chooser.showSaveDialog(window);
        if (file == null) return;
        exportButton.setDisable(true);
        var task = async.run(() -> service.export(study.id(), file.toPath()), path -> {
            exportButton.setDisable(false);
            exportBusy.finish();
            var alert = new Alert(Alert.AlertType.INFORMATION, "Report exported to " + path, ButtonType.OK);
            alert.setHeaderText("Export complete");
            alert.showAndWait();
        }, failure -> {
            exportButton.setDisable(false);
            exportBusy.finish();
            errors.accept(failure);
        });
        exportBusy.start(task, () -> exportButton.setDisable(false));
    }

    private static String sanitizeFileName(String title) {
        var cleaned = title.strip().replaceAll("[^A-Za-z0-9-]", "-");
        return cleaned.isBlank() ? "study" : cleaned;
    }

    private static Label sectionLabel(String text) {
        var label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    private static Label wrapped(String text) {
        var label = new Label(text);
        label.setWrapText(true);
        return label;
    }

    private static Label muted(String text) {
        var label = new Label(text);
        label.getStyleClass().add("muted");
        label.setWrapText(true);
        return label;
    }
}
