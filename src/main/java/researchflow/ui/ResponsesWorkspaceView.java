package researchflow.ui;

import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;
import researchflow.domain.ResponseSummary;
import researchflow.domain.Study;
import researchflow.service.ResponseQueryService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
public final class ResponsesWorkspaceView {
    private final VBox root = new VBox(16);
    private final TableView<ResponseSummary> table = new TableView<>();

    public ResponsesWorkspaceView(Study study, ResponseQueryService responses, Async async, Consumer<Throwable> errors) {
        root.setPadding(new Insets(28));
        var eyebrow = new Label("RESEARCHER MODE"); eyebrow.getStyleClass().add("eyebrow");
        var title = new Label("Responses"); title.getStyleClass().add("page-title");
        var form = new TableColumn<ResponseSummary, String>("Form");
        form.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().formTitle()));
        var submitted = new TableColumn<ResponseSummary, String>("Submitted");
        submitted.setCellValueFactory(row -> new SimpleStringProperty(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                .withZone(ZoneId.systemDefault()).format(row.getValue().submittedAt())));
        var count = new TableColumn<ResponseSummary, String>("Answers");
        count.setCellValueFactory(row -> new SimpleStringProperty(Integer.toString(row.getValue().answerCount())));
        var duration = new TableColumn<ResponseSummary, String>("Duration (seconds)");
        duration.setCellValueFactory(row -> new SimpleStringProperty(row.getValue().durationSeconds() == null
                ? "—" : row.getValue().durationSeconds().toString()));
        table.getColumns().addAll(form, submitted, count, duration);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No responses have been submitted yet."));
        root.getChildren().addAll(eyebrow, title, table);
        async.run(() -> responses.list(study.id()), values -> table.getItems().setAll(values), errors);
    }

    public Parent node() { return root; }
}

