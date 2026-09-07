package researchflow.ui;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import researchflow.domain.DatasetVersion;
import researchflow.domain.Study;
import researchflow.service.ValidationException;
import researchflow.service.VersionService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Dataset version history: snapshot creation, active-version tracking, and safe restore. */
public final class DatasetVersionsView {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final VBox root = new VBox(12);
    private final TableView<DatasetVersion> table = new TableView<>();
    private final Study study;
    private final VersionService versions;
    private final Async async;
    private final Consumer<Throwable> errors;
    private Map<UUID, Integer> versionNumbers = new HashMap<>();

    public DatasetVersionsView(Study study, VersionService versions, Async async, Consumer<Throwable> errors) {
        this.study = study;
        this.versions = versions;
        this.async = async;
        this.errors = errors;
        root.setPadding(new Insets(20));

        var eyebrow = new Label("DATASET VERSIONS");
        eyebrow.getStyleClass().add("eyebrow");
        var title = new Label("Version history");
        title.getStyleClass().add("page-title");

        var create = new Button("Create version snapshot");
        create.getStyleClass().add("primary-button");
        create.setOnAction(event -> createVersion());
        var restore = new Button("Restore selected version");
        restore.disableProperty().bind(Bindings.createBooleanBinding(
                () -> table.getSelectionModel().getSelectedItem() == null || table.getSelectionModel().getSelectedItem().active(),
                table.getSelectionModel().selectedItemProperty()));
        restore.setOnAction(event -> restore(table.getSelectionModel().getSelectedItem()));
        var toolbar = new HBox(10, create, restore);

        table.setPlaceholder(new Label("No dataset versions yet. Create one after a cleaning batch."));
        buildColumns();
        root.getChildren().addAll(eyebrow, title, toolbar, table);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        refresh();
    }

    public Parent node() {
        return root;
    }

    private void buildColumns() {
        var number = new TableColumn<DatasetVersion, String>("Version");
        number.setCellValueFactory(row -> new SimpleStringProperty(Integer.toString(row.getValue().versionNumber())));
        var active = new TableColumn<DatasetVersion, String>("Active");
        active.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().active() ? "Yes" : "No"));
        var parent = new TableColumn<DatasetVersion, String>("Parent");
        parent.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().parentVersionId() == null
                ? "—" : "v" + versionNumbers.getOrDefault(row.getValue().parentVersionId(), 0)));
        var created = new TableColumn<DatasetVersion, String>("Created");
        created.setCellValueFactory(row -> new SimpleStringProperty(TIMESTAMP.format(row.getValue().createdAt())));
        var reason = new TableColumn<DatasetVersion, String>("Reason");
        reason.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().reason()));
        reason.setPrefWidth(260);
        var summary = new TableColumn<DatasetVersion, String>("Change summary");
        summary.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().changeSummary()));
        summary.setPrefWidth(260);
        table.getColumns().setAll(List.of(number, active, parent, created, reason, summary));
    }

    private void refresh() {
        table.setDisable(true);
        async.run(() -> versions.list(study.id()), list -> {
            table.setDisable(false);
            versionNumbers = new HashMap<>();
            for (var version : list) versionNumbers.put(version.id(), version.versionNumber());
            table.getItems().setAll(list);
            table.refresh();
        }, errors);
    }

    private void createVersion() {
        var dialog = new Dialog<String[]>();
        dialog.setTitle("Create version snapshot");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);
        var reason = new TextArea();
        reason.setPromptText("Why is this snapshot being created?");
        reason.setPrefRowCount(3);
        var summary = new TextField();
        summary.setPromptText("Change summary (optional)");
        var error = new Label();
        error.getStyleClass().add("field-error");
        dialog.getDialogPane().setContent(new VBox(10, new Label("Reason"), reason,
                new Label("Change summary"), summary, error));
        var ok = dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (reason.getText().strip().length() < 3) { error.setText("Provide a reason of at least 3 characters."); event.consume(); }
        });
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new String[]{reason.getText().strip(), summary.getText().strip()} : null);
        dialog.showAndWait().ifPresent(values -> {
            table.setDisable(true);
            async.run(() -> versions.createSnapshot(study.id(), values[0], values[1]), ignored -> refresh(),
                    failure -> { table.setDisable(false); handle(failure); });
        });
    }

    private void restore(DatasetVersion target) {
        if (target == null || target.active()) return;
        ReasonDialog.create("Restore version " + target.versionNumber(),
                "Restoring creates a new version rather than rewriting history. Why is this restore needed?",
                "Restore reason").showAndWait().ifPresent(reason -> {
            var confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Restore live data to match version " + target.versionNumber() + "? This creates a new version.",
                    ButtonType.CANCEL, ButtonType.OK);
            confirm.setHeaderText("Confirm version restore");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            table.setDisable(true);
            async.run(() -> versions.restore(study.id(), target.id(), reason), ignored -> refresh(),
                    failure -> { table.setDisable(false); handle(failure); });
        });
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
