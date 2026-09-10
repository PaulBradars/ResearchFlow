package researchflow.ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import researchflow.app.AppServices;
import researchflow.domain.Study;

import java.util.concurrent.ExecutorService;

public final class Navigator {
    private final AppServices services;
    private Async async;
    private final ExecutorService executor;
    private final ErrorBoundary boundary = new ErrorBoundary();
    private final BorderPane shell = new BorderPane();

    public Navigator(AppServices services, ExecutorService executor) {
        this.services = services;
        this.executor = executor;
        this.async = new Async(executor, services.connections());
        shell.setTop(topBar());
        shell.setCenter(boundary.node());
        showStudies();
    }

    public Parent root() {
        return shell;
    }

    public void showStudies() {
        newScope();
        shell.setLeft(null);
        boundary.show(new StudiesHomeView(services.studies(), async, this::showDashboard, this::showError).node());
    }

    public void showDashboard(Study study) {
        newScope();
        shell.setLeft(studyNavigation(study));
        markNavigation("Dashboard");
        boundary.show(new StudyDashboardView(study, services.studies(), services.analysis(), async,
                this::showError, () -> showAnalysis(study)).node());
    }

    public void showForms(Study study) {
        newScope();
        shell.setLeft(studyNavigation(study));
        markNavigation("Form");
        boundary.show(new FormWorkspaceView(study, services.forms(), async,
                form -> showRespondent(study, form), services::collectionLink, this::showError).node());
    }

    public void showResponses(Study study) {
        newScope();
        shell.setLeft(studyNavigation(study));
        markNavigation("Responses");
        boundary.show(new ResponsesWorkspaceView(study, services.responses(), async, this::showError).node());
    }

    public void showDataset(Study study) {
        newScope();
        shell.setLeft(studyNavigation(study));
        markNavigation("Dataset");
        boundary.show(new DatasetWorkspaceView(study, services.datasets(), services.corrections(),
                services.audits(), async, this::showError).node());
    }

    public void showImport(Study study) {
        newScope();
        shell.setLeft(studyNavigation(study));
        markNavigation("Import");
        boundary.show(new ImportWorkspaceView(study, services.imports(), async,
                () -> showDataset(study), this::showError).node());
    }

    public void showQuality(Study study) {
        newScope();
        shell.setLeft(studyNavigation(study));
        markNavigation("Quality / Versions");
        boundary.show(new QualityWorkspaceView(study, services.quality(), services.qualityReview(),
                services.versions(), services.datasets(), async, this::showError).node());
    }

    public void showAnalysis(Study study) {
        newScope();
        shell.setLeft(studyNavigation(study));
        markNavigation("Analysis");
        boundary.show(new AnalysisWorkspaceView(study, services.analysis(), services.aiFacade(), services.findings(),
                services.datasets(), services.versions(), async, this::showError).node());
    }

    public void showFindings(Study study) {
        newScope();
        shell.setLeft(studyNavigation(study));
        markNavigation("Findings / Report");
        boundary.show(new FindingsReportWorkspaceView(study, services.findings(), services.reports(),
                async, this::showError).node());
    }

    private void showRespondent(Study study, researchflow.domain.Form form) {
        newScope();
        shell.setLeft(studyNavigation(study));
        boundary.show(new RespondentEntryView(form, services.submissions(), async,
                () -> showResponses(study), this::showError).node());
    }

    private void newScope() {
        async.close();
        async = new Async(executor, services.connections());
    }

    private void showRecovery() {
        async.close();
        async = new Async(executor);
        shell.setLeft(null);
        boundary.show(new RecoveryView(services.recovery(), async, this::showStudies, busy -> shell.getTop().setDisable(busy)).node());
    }

    private Parent topBar() {
        var brand = new Label("ResearchFlow AI");
        brand.setTooltip(new javafx.scene.control.Tooltip(services.config().aiDiagnostics()));
        brand.getStyleClass().add("brand");
        var bar = new BorderPane();
        bar.setLeft(brand);
        var recovery = new Button("Backup / Recovery"); recovery.setOnAction(event -> showRecovery());
        var cancel = new Button("Cancel workspace tasks");
        cancel.setOnAction(event -> { async.cancelAll(); showStudies(); });
        bar.setRight(new javafx.scene.layout.HBox(8, cancel, recovery));
        bar.setPadding(new Insets(14, 22, 14, 22));
        bar.getStyleClass().add("top-bar");
        return bar;
    }

    private Parent studyNavigation(Study study) {
        var back = navButton("← All studies", this::showStudies);
        var dashboard = navButton("Dashboard", () -> showDashboard(study));
        var disabled = new Label("WORKSPACES");
        disabled.getStyleClass().add("eyebrow");
        var form = navButton("Form", () -> showForms(study));
        var responses = navButton("Responses", () -> showResponses(study));
        var importData = navButton("Import", () -> showImport(study));
        var dataset = navButton("Dataset", () -> showDataset(study));
        var quality = navButton("Quality / Versions", () -> showQuality(study));
        var analysis = navButton("Analysis", () -> showAnalysis(study));
        var findings = navButton("Findings / Report", () -> showFindings(study));
        var spacer = new javafx.scene.layout.Region(); VBox.setVgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        var footer = new Label("LOCAL WORKSPACE\nYour data stays on this PC"); footer.getStyleClass().add("nav-footer");
        var nav = new VBox(6, back, dashboard, disabled, form, responses, importData, dataset, quality, analysis, findings, spacer, footer);
        nav.setPadding(new Insets(20, 14, 20, 14));
        nav.getStyleClass().add("side-nav");
        nav.setPrefWidth(228);
        return nav;
    }

    private void markNavigation(String title) {
        if (shell.getLeft() instanceof VBox navigation) for (var child : navigation.getChildren()) {
            child.getStyleClass().remove("nav-active");
            if (child instanceof Button button && button.getText().equals(title)) child.getStyleClass().add("nav-active");
        }
    }

    private Button navButton(String text, Runnable action) {
        var button = new Button(text);
        button.getStyleClass().add("nav-button");
        button.setGraphic(Visuals.icon(text));
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> action.run());
        return button;
    }

    private void showError(Throwable failure) {
        var message = failure.getMessage();
        boundary.showError(message == null || message.isBlank()
                ? "The operation could not be completed. Your saved data was not changed." : message, failure);
    }
}
