package researchflow.ui;

import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;
import researchflow.domain.AnalysisSummary;
import researchflow.domain.HistoricalEvidence;
import researchflow.domain.Study;
import researchflow.service.AnalysisService;
import researchflow.visualization.ChartBuilder;
import researchflow.visualization.ChartSpec;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Dashboard chart browser, scoped to persisted analyses and their original dataset snapshots. */
public final class DashboardDiagramsView {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private final VBox root = new VBox(12);
    private final VBox content = new VBox(12);
    private final ComboBox<AnalysisSummary> selection = new ComboBox<>();
    private final Button refresh = new Button("Refresh diagrams");
    private final Label status = new Label();
    private final Study study;
    private final AnalysisService analysis;
    private final Async async;

    public DashboardDiagramsView(Study study, AnalysisService analysis, Async async, Runnable openAnalysis) {
        this.study = study;
        this.analysis = analysis;
        this.async = async;
        root.setId("dashboard-diagrams");
        root.getStyleClass().add("content-panel");
        var heading = new Label("Statistical diagrams");
        heading.getStyleClass().add("section-title");
        var hint = label("Explore saved AI and manual analyses. Choose a result to view its chart and statistical evidence.");
        hint.getStyleClass().add("muted");
        selection.setId("dashboard-diagram-selection");
        selection.setPromptText("Choose a saved analysis");
        selection.setPrefWidth(520);
        selection.setMinWidth(0);
        selection.setMaxWidth(Double.MAX_VALUE);
        selection.setConverter(new StringConverter<>() {
            @Override public String toString(AnalysisSummary value) {
                return value == null ? "" : value.method() + " · v" + value.versionNumber() + " · n="
                        + value.sampleSize() + " · " + value.source() + " · " + DATE.format(value.createdAt());
            }
            @Override public AnalysisSummary fromString(String value) { throw new UnsupportedOperationException(); }
        });
        selection.setOnAction(event -> { if (selection.getValue() != null) load(selection.getValue()); });
        refresh.setOnAction(event -> refresh());
        var open = new Button("Open analysis workspace");
        open.setOnAction(event -> openAnalysis.run());
        var controls = new FlowPane(8, 8, refresh, open);
        status.setWrapText(true);
        status.setId("dashboard-diagram-status");
        content.setId("dashboard-diagram-content");
        root.getChildren().addAll(heading, hint, selection, controls, status, content);
        refresh();
    }

    public Parent node() { return root; }

    private void busy(boolean busy) {
        selection.setDisable(busy);
        refresh.setDisable(busy);
    }

    private void refresh() {
        UUID previous = selection.getValue() == null ? null : selection.getValue().id();
        busy(true);
        status.setText("Loading saved analyses…");
        content.getChildren().clear();
        async.run(() -> analysis.history(study.id()), summaries -> {
            selection.setOnAction(null);
            selection.getItems().setAll(summaries);
            busy(false);
            if (summaries.isEmpty()) {
                status.setText("No statistical diagrams yet. Run an analysis or ask a data question in the Analysis workspace, then refresh here.");
            } else {
                var selected = summaries.stream().filter(item -> item.id().equals(previous)).findFirst()
                        .orElse(summaries.getFirst());
                // Explicit loading also refreshes the chart when the selected record has not changed.
                selection.setValue(selected);
                selection.setOnAction(event -> { if (selection.getValue() != null) load(selection.getValue()); });
                load(selected);
            }
        }, this::failed);
    }

    private void load(AnalysisSummary summary) {
        busy(true);
        content.getChildren().clear();
        status.setText("Loading diagram…");
        async.run(() -> {
            var historical = analysis.reopen(study.id(), summary.id());
            Optional<ChartSpec> chart = Optional.empty();
            if (historical.supported()) {
                var result = historical.evidence().result();
                boolean needsValues = result instanceof researchflow.domain.AnalysisResult.NumericSummary
                        || result instanceof researchflow.domain.AnalysisResult.Correlation;
                var values = needsValues ? analysis.chartValues(study.id(), historical.evidence()) : null;
                chart = ChartBuilder.build(historical.evidence(), values == null ? List.of() : values.primary(),
                        values == null ? null : values.secondary());
            }
            return new Loaded(historical, chart, analysis.provenance(study.id(), summary.id()));
        }, loaded -> {
            busy(false);
            status.setText("Saved analysis · " + summary.source() + " · dataset v" + summary.versionNumber()
                    + " · sample size " + summary.sampleSize());
            if (!loaded.historical().supported()) {
                content.getChildren().add(label(loaded.historical().notice()));
                return;
            }
            if (loaded.chart().isPresent()) {
                var chart = ChartView.render(loaded.chart().get());
                chart.setId("dashboard-statistical-chart");
                if (chart instanceof javafx.scene.layout.Region region) {
                    region.setMinWidth(0);
                    region.setPrefHeight(360);
                }
                content.getChildren().add(chart);
            } else content.getChildren().add(label("This saved analysis has no numeric observations to plot."));
            var evidence = new TitledPane("Statistical results and warnings", EvidenceView.render(loaded.historical().evidence()));
            evidence.setExpanded(true);
            evidence.setAnimated(false);
            var provenance = new TitledPane("Dataset and analysis provenance", label(loaded.provenance()));
            provenance.setExpanded(false);
            provenance.setAnimated(false);
            content.getChildren().addAll(evidence, provenance);
        }, this::failed);
    }

    private void failed(Throwable failure) {
        busy(false);
        content.getChildren().clear();
        status.setText("Could not load diagrams. " + (failure.getMessage() == null ? "Try Refresh diagrams." : failure.getMessage()));
    }

    private static Label label(String text) { var label = new Label(text); label.setWrapText(true); return label; }
    private record Loaded(HistoricalEvidence historical, Optional<ChartSpec> chart, String provenance) { }
}
