package researchflow.ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import researchflow.domain.Form;
import researchflow.service.ResponseSubmissionService;
import researchflow.service.ValidationException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.function.Consumer;

public final class RespondentEntryView {
    private final ScrollPane root = new ScrollPane();
    private final Instant startedAt = Instant.now();

    public RespondentEntryView(Form form, ResponseSubmissionService submissions, Async async,
                               Runnable submitted, Consumer<Throwable> errors) {
        var content = new VBox(16);
        content.setPadding(new Insets(28));
        content.setMaxWidth(760);
        var eyebrow = new Label("RESPONDENT MODE");
        eyebrow.getStyleClass().add("eyebrow");
        var title = new Label(form.title());
        title.getStyleClass().add("page-title");
        var description = new Label(form.description());
        description.setWrapText(true); description.getStyleClass().add("muted");
        var controls = new LinkedHashMap<UUID, QuestionControlFactory.QuestionControl>();
        content.getChildren().addAll(eyebrow, title, description);
        for (var section : form.sections()) {
            if (!section.title().isBlank()) {
                var sectionTitle = new Label(section.title()); sectionTitle.getStyleClass().add("section-title");
                content.getChildren().add(sectionTitle);
            }
            for (var question : section.questions()) {
                var control = QuestionControlFactory.create(question);
                controls.put(question.id(), control);
                content.getChildren().add(control.node());
            }
        }
        var submit = new Button("Submit response");
        submit.getStyleClass().add("primary-button");
        submit.setOnAction(event -> {
            controls.values().forEach(control -> control.showError(null));
            var raw = new LinkedHashMap<UUID, String>();
            controls.forEach((id, control) -> raw.put(id, control.value().get()));
            submit.setDisable(true);
            async.run(() -> submissions.submit(form.id(), startedAt, raw), response -> submitted.run(), failure -> {
                submit.setDisable(false);
                if (failure instanceof ValidationException validation) {
                    validation.errors().forEach((key, message) -> {
                        try {
                            var control = controls.get(UUID.fromString(key));
                            if (control != null) control.showError(message);
                        } catch (IllegalArgumentException ignored) { }
                    });
                } else errors.accept(failure);
            });
        });
        content.getChildren().add(submit);
        root.setFitToWidth(true); root.setContent(content);
    }

    public Parent node() { return root; }
}

