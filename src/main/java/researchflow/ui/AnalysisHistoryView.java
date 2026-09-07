package researchflow.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import researchflow.domain.AnalysisSummary;
import researchflow.domain.Study;
import researchflow.service.AnalysisService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/** Every persisted analysis: method, version, sample size, source, and a raw evidence details view. */
public final class AnalysisHistoryView {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final VBox root = new VBox(12);
    private final TableView<AnalysisSummary> table = new TableView<>();
    private final Study study;
    private final AnalysisService analysis;
    private final Async async;
    private final Consumer<Throwable> errors;

    public AnalysisHistoryView(Study study, AnalysisService analysis, Async async, Consumer<Throwable> errors) {
        this.study = study;
        this.analysis = analysis;
        this.async = async;
        this.errors = errors;
        root.setPadding(new Insets(20));

        var eyebrow = new Label("ANALYSIS HISTORY");
        eyebrow.getStyleClass().add("eyebrow");
        var title = new Label("Past analyses");
        title.getStyleClass().add("page-title");

        table.setPlaceholder(new Label("No analyses have been run yet."));
        buildColumns();
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                showDetails(table.getSelectionModel().getSelectedItem());
            }
        });
        var details = new Button("View details");
        details.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        details.setOnAction(event -> showDetails(table.getSelectionModel().getSelectedItem()));
        var refresh = new Button("Refresh");
        refresh.setOnAction(event -> refresh());

        root.getChildren().addAll(eyebrow, title, new HBox(8, refresh, details), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        refresh();
    }

    public Parent node() {
        return root;
    }

    public void refresh() {
        table.setDisable(true);
        async.run(() -> analysis.history(study.id()), list -> { table.setDisable(false); table.getItems().setAll(list); }, errors);
    }

    private void buildColumns() {
        var method = new TableColumn<AnalysisSummary, String>("Method");
        method.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().method().toString()));
        var version = new TableColumn<AnalysisSummary, String>("Version");
        version.setCellValueFactory(row -> new SimpleStringProperty("v" + row.getValue().versionNumber()));
        var sampleSize = new TableColumn<AnalysisSummary, String>("Sample size");
        sampleSize.setCellValueFactory(row -> new SimpleStringProperty(Integer.toString(row.getValue().sampleSize())));
        var source = new TableColumn<AnalysisSummary, String>("Source");
        source.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().source()));
        var created = new TableColumn<AnalysisSummary, String>("Run at");
        created.setCellValueFactory(row -> new SimpleStringProperty(TIMESTAMP.format(row.getValue().createdAt())));
        table.getColumns().setAll(List.of(method, version, sampleSize, source, created));
    }

    private void showDetails(AnalysisSummary summary) {
        if (summary == null) return;
        async.run(() -> analysis.details(summary.id()), stored -> {
            var content = new TextArea("Plan:\n" + stored.planJson() + "\n\nResult:\n" + stored.resultJson()
                    + "\n\nWarnings:\n" + stored.warningsJson());
            content.setEditable(false);
            content.setWrapText(true);
            content.setPrefSize(560, 360);
            var dialog = new Dialog<Void>();
            dialog.setTitle("Analysis details");
            dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            dialog.getDialogPane().setContent(content);
            dialog.showAndWait();
        }, errors);
    }
}
