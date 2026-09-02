package researchflow.ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import researchflow.domain.AuditEvent;
import researchflow.domain.Study;
import researchflow.service.AuditService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

public final class AuditTimelineView {
    private final VBox root = new VBox(12);

    public AuditTimelineView(Study study, AuditService audits, Async async, Consumer<Throwable> errors) {
        root.setPadding(new Insets(18));
        var list = new ListView<AuditEvent>();
        list.setPlaceholder(new Label("No audit events recorded yet."));
        var format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
        list.setCellFactory(view -> new ListCell<>() {
            @Override protected void updateItem(AuditEvent item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                var event = new Label(item.eventType().replace('_', ' ')); event.getStyleClass().add("question-label");
                var metadata = new Label(format.format(item.occurredAt()) + " · " + item.actor()
                        + " · " + item.entityType()); metadata.getStyleClass().add("muted");
                setGraphic(new VBox(3, event, metadata));
            }
        });
        root.getChildren().addAll(new Label("Newest 250 events"), list);
        VBox.setVgrow(list, javafx.scene.layout.Priority.ALWAYS);
        async.run(() -> audits.timeline(study.id()), values -> list.getItems().setAll(values), errors);
    }

    public Parent node() { return root; }
}
