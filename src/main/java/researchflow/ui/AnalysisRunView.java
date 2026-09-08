package researchflow.ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import researchflow.domain.AnalysisFilter;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.DatasetFilterOperator;
import researchflow.domain.DatasetQuery;
import researchflow.domain.DatasetVariable;
import researchflow.domain.DatasetVersion;
import researchflow.domain.EvidenceBundle;
import researchflow.domain.Study;
import researchflow.service.AnalysisService;
import researchflow.service.DatasetService;
import researchflow.service.FindingService;
import researchflow.service.ValidationException;
import researchflow.service.VersionService;

import java.util.List;
import java.util.function.Consumer;

public final class AnalysisRunView {
    private final VBox root = new VBox(14);
    private final ComboBox<AnalysisMethod> method = new ComboBox<>();
    private final ComboBox<DatasetVariable> primary = new ComboBox<>();
    private final ComboBox<DatasetVariable> secondary = new ComboBox<>();
    private final ComboBox<DatasetVariable> filterVariable = new ComboBox<>();
    private final ComboBox<DatasetFilterOperator> filterOperator = new ComboBox<>();
    private final TextField filterValue = new TextField();
    private final ComboBox<DatasetVersion> version = new ComboBox<>();
    private final Label validation = new Label();
    private final BusyState runBusy = new BusyState();
    private final VBox evidence = new VBox(10);
    private final Study study;
    private final AnalysisService analysisService;
    private final FindingService findingService;
    private final VersionService versionService;
    private final Async async;
    private final Consumer<Throwable> errors;

    public AnalysisRunView(Study study, AnalysisService analysisService, FindingService findingService,
                           DatasetService datasets, VersionService versionService, Async async, Consumer<Throwable> errors) {
        this.study = study;
        this.analysisService = analysisService;
        this.findingService = findingService;
        this.versionService = versionService;
        this.async = async;
        this.errors = errors;
        root.setPadding(new Insets(20));

        var eyebrow = new Label("ANALYSIS / MANUAL");
        eyebrow.getStyleClass().add("eyebrow");
        var title = new Label("Run an analysis");
        title.getStyleClass().add("page-title");

        method.getItems().setAll(AnalysisMethod.values());
        method.setValue(AnalysisMethod.FREQUENCY);
        method.valueProperty().addListener((obs, old, value) -> updateSecondaryAvailability(value));
        primary.setPromptText("Primary variable");
        primary.setPrefWidth(240);
        secondary.setPromptText("Secondary variable");
        secondary.setPrefWidth(240);
        filterVariable.setPromptText("Filter variable (optional)");
        filterVariable.setPrefWidth(200);
        filterOperator.getItems().setAll(DatasetFilterOperator.values());
        filterOperator.setPromptText("Operator");
        filterValue.setPromptText("Filter value");
        filterOperator.valueProperty().addListener((obs, old, value) ->
                filterValue.setDisable(value == DatasetFilterOperator.IS_MISSING));
        version.setPromptText("Active (default)");
        version.setPrefWidth(280);
        version.setConverter(new StringConverter<DatasetVersion>() {
            @Override public String toString(DatasetVersion value) {
                return value == null ? "Active (default, auto-creates a baseline if none exists)"
                        : "v" + value.versionNumber() + (value.active() ? " · active" : "") + " — " + value.reason();
            }
            @Override public DatasetVersion fromString(String string) { return null; }
        });

        var run = new Button("Run analysis");
        run.getStyleClass().add("primary-button");
        run.setOnAction(event -> run());
        var runBar = new HBox(10, run, runBusy.node());

        var form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Method"), method);
        form.addRow(1, new Label("Primary variable"), primary);
        form.addRow(2, new Label("Secondary variable"), secondary);
        form.addRow(3, new Label("Filter"), new HBox(8, filterVariable, filterOperator, filterValue));
        form.addRow(4, new Label("Dataset version"), version);

        validation.getStyleClass().add("field-error");
        var evidenceTitle = new Label("Evidence");
        evidenceTitle.getStyleClass().add("section-title");
        root.getChildren().addAll(eyebrow, title, form, runBar, validation, new Separator(), evidenceTitle, evidence);

        updateSecondaryAvailability(method.getValue());
        async.run(() -> datasets.query(study.id(), DatasetQuery.firstPage()), page -> {
            primary.getItems().setAll(page.variables());
            secondary.getItems().setAll(page.variables());
            filterVariable.getItems().setAll(page.variables());
        }, errors);
        refreshVersions();
    }

    public Parent node() {
        return root;
    }

    private void updateSecondaryAvailability(AnalysisMethod value) {
        var needsSecondary = value == AnalysisMethod.CORRELATION || value == AnalysisMethod.CROSS_TABULATION
                || value == AnalysisMethod.GROUP_COMPARISON;
        secondary.setDisable(!needsSecondary);
    }

    private void refreshVersions() {
        async.run(() -> versionService.list(study.id()), list -> {
            var current = version.getValue();
            version.getItems().setAll(list);
            version.getItems().add(0, null);
            version.setValue(current != null && list.stream().anyMatch(v -> v.id().equals(current.id())) ? current : null);
        }, errors);
    }

    private void run() {
        validation.setText("");
        if (primary.getValue() == null) {
            validation.setText("Choose a primary variable.");
            return;
        }
        var filters = filterVariable.getValue() == null || filterOperator.getValue() == null ? List.<AnalysisFilter>of()
                : List.of(new AnalysisFilter(filterVariable.getValue().questionId(), filterOperator.getValue(), filterValue.getText()));
        var plan = new AnalysisPlan(method.getValue(), primary.getValue().questionId(),
                secondary.getValue() == null ? null : secondary.getValue().questionId(), filters,
                version.getValue() == null ? null : version.getValue().id());
        var task = async.run(() -> analysisService.run(study.id(), plan), evidence -> {
            runBusy.finish();
            showEvidence(evidence);
        }, failure -> {
            runBusy.finish();
            if (failure instanceof ValidationException issue) validation.setText(String.join(" ", issue.errors().values()));
            else errors.accept(failure);
        });
        runBusy.start(task);
    }

    private void showEvidence(EvidenceBundle bundle) {
        refreshVersions();
        evidence.getChildren().setAll(EvidenceView.render(bundle));
        evidence.getChildren().add(EvidenceActions.createFindingButton(study, bundle, analysisService, findingService, async, errors));
    }
}

