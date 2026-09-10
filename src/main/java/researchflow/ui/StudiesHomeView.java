package researchflow.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import researchflow.domain.Study;
import researchflow.service.StudyService;
import researchflow.service.ValidationException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public final class StudiesHomeView {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM uuuu")
            .withZone(ZoneId.systemDefault());
    private final StudyService studies;
    private final Async async;
    private final Consumer<Study> openStudy;
    private final Consumer<Throwable> errors;
    private final TableView<Study> table = new TableView<>();
    private final BorderPane root = new BorderPane();
    private final CheckBox archived = new CheckBox("Show archived");
    private final Label status = new Label("Loading studies…");

    public StudiesHomeView(StudyService studies, Async async, Consumer<Study> openStudy, Consumer<Throwable> errors) {
        this.studies = studies;
        this.async = async;
        this.openStudy = openStudy;
        this.errors = errors;
        configure();
        refresh();
    }

    public Parent node() {
        return root;
    }

    private void configure() {
        root.setPadding(new Insets(28));
        var title = new Label("Studies");
        title.getStyleClass().add("page-title");
        var subtitle = new Label("Create, open, and archive local research workspaces.");
        subtitle.getStyleClass().add("muted");
        var heading = Visuals.hero("YOUR RESEARCH WORKSPACE", "Good research starts here.",
                "Design studies, collect responses, and turn your data into evidence.");

        var create = new Button("New study");
        create.getStyleClass().add("primary-button");
        create.setOnAction(event -> createStudy());
        var edit = new Button("Edit");
        edit.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        edit.setOnAction(event -> editStudy());
        var archive = new Button("Archive");
        archive.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        archive.setOnAction(event -> archiveStudy());
        archived.setOnAction(event -> refresh());
        var actions = new HBox(10, create, edit, archive, archived);
        actions.setPadding(new Insets(18, 0, 12, 0));

        var name = new TableColumn<Study, String>("Study");
        name.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().title()));
        name.setPrefWidth(340);
        var researcher = new TableColumn<Study, String>("Researcher");
        researcher.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().researcher()));
        researcher.setPrefWidth(220);
        var state = new TableColumn<Study, String>("Status");
        state.setCellValueFactory(value -> new SimpleStringProperty(value.getValue().status().name()));
        state.setPrefWidth(110);
        var updated = new TableColumn<Study, String>("Updated");
        updated.setCellValueFactory(value -> new SimpleStringProperty(DATE.format(value.getValue().updatedAt())));
        updated.setPrefWidth(140);
        table.getColumns().add(name);
        table.getColumns().add(researcher);
        table.getColumns().add(state);
        table.getColumns().add(updated);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No studies yet. Create the first research workspace."));
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                openStudy.accept(table.getSelectionModel().getSelectedItem());
            }
        });

        root.setTop(new VBox(heading, actions));
        root.setCenter(table);
        root.setBottom(status);
        BorderPane.setMargin(status, new Insets(10, 0, 0, 0));
        status.getStyleClass().add("muted");
    }

    private void refresh() {
        status.setText("Loading studies…");
        async.run(() -> studies.list(archived.isSelected()), loaded -> {
            table.setItems(FXCollections.observableArrayList(loaded));
            status.setText(loaded.size() + (loaded.size() == 1 ? " study" : " studies"));
        }, errors);
    }

    private void createStudy() {
        new StudyEditorDialog(null).showAndWait().ifPresent(values -> async.run(
                () -> studies.create(values.title(), values.description(), values.objectives(), values.researcher(),
                        values.startDate(), values.endDate(), values.researchQuestions()),
                created -> { refresh(); openStudy.accept(created); }, this::handleFailure));
    }

    private void editStudy() {
        var selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        new StudyEditorDialog(selected).showAndWait().ifPresent(values -> async.run(
                () -> studies.update(selected.id(), values.title(), values.description(), values.objectives(),
                        values.researcher(), values.startDate(), values.endDate(), values.researchQuestions()),
                updated -> refresh(), this::handleFailure));
    }

    private void archiveStudy() {
        var selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        var confirmation = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Archive “" + selected.title() + "”? Its data stays in the local database.",
                javafx.scene.control.ButtonType.CANCEL, javafx.scene.control.ButtonType.OK);
        confirmation.setHeaderText("Archive study");
        confirmation.showAndWait().filter(button -> button == javafx.scene.control.ButtonType.OK)
                .ifPresent(button -> async.run(() -> studies.archive(selected.id()), this::refresh, this::handleFailure));
    }

    private void handleFailure(Throwable failure) {
        if (failure instanceof ValidationException validation) {
            errors.accept(new IllegalArgumentException(String.join(" ", validation.errors().values()), validation));
        } else {
            errors.accept(failure);
        }
    }
}
