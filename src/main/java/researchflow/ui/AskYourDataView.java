package researchflow.ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import researchflow.ai.LlmException;
import researchflow.domain.ChatMessage;
import researchflow.domain.ChatRole;
import researchflow.domain.Study;
import researchflow.service.AnalysisFacade;
import researchflow.service.AnalysisService;
import researchflow.service.FindingService;
import researchflow.service.ValidationException;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

/**
 * "Ask Your Data": a natural-language question is turned into a validated, executed, persisted
 * analysis exactly like the manual panel, then explained in plain language — never the reverse.
 */
public final class AskYourDataView {
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final VBox root = new VBox(14);
    private final VBox transcript = new VBox(12);
    private final TextField question = new TextField();
    private final Label status = new Label();
    private final Label progress = new Label();
    private final Button ask = new Button("Ask");
    private final BusyState askBusy = new BusyState();
    private final Study study;
    private final AnalysisFacade facade;
    private final AnalysisService analysisService;
    private final FindingService findingService;
    private final Async async;
    private final Consumer<Throwable> errors;

    public AskYourDataView(Study study, AnalysisFacade facade, AnalysisService analysisService,
                           FindingService findingService, Async async, Consumer<Throwable> errors) {
        this.study = study;
        this.facade = facade;
        this.analysisService = analysisService;
        this.findingService = findingService;
        this.async = async;
        this.errors = errors;
        root.setPadding(new Insets(20));

        var eyebrow = new Label("ASK YOUR DATA");
        eyebrow.getStyleClass().add("eyebrow");
        var title = new Label("Ask a question in plain language");
        title.getStyleClass().add("page-title");

        question.setPromptText("e.g. How is sleep duration associated with academic focus?");
        ask.getStyleClass().add("primary-button");
        ask.setOnAction(event -> ask());
        var bar = new HBox(8, question, ask);
        HBox.setHgrow(question, Priority.ALWAYS);

        status.getStyleClass().add("field-error");
        status.setWrapText(true);
        progress.getStyleClass().add("muted");
        progress.setWrapText(true);
        var progressBar = new HBox(8, progress, askBusy.node());
        transcript.setPadding(new Insets(4));
        var scroll = new ScrollPane(transcript);
        scroll.setFitToWidth(true);

        root.getChildren().addAll(eyebrow, title, bar, status, progressBar, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        checkAvailability();
        refresh();
    }

    public Parent node() {
        return root;
    }

    private void checkAvailability() {
        async.run(facade::aiAvailable, available -> {
            ask.setDisable(!available);
            status.setText(available ? ""
                    : "Local AI is not available right now. Manual analysis still works from the Run analysis tab.");
        }, errors);
    }

    private void refresh() {
        async.run(() -> facade.history(study.id()), this::showHistory, errors);
    }

    private void showHistory(List<ChatMessage> messages) {
        transcript.getChildren().clear();
        for (var message : messages) {
            transcript.getChildren().add(bubble(speakerFor(message.role()), message.content(), message.createdAt()));
        }
    }

    private void ask() {
        var text = question.getText();
        status.setText("");
        ask.setDisable(true);
        progress.setText("Thinking… a local model can take up to a minute, especially on the first "
                + "request while it loads.");
        var task = async.run(() -> facade.ask(study.id(), text), answer -> {
            progress.setText("");
            askBusy.finish();
            question.clear();
            checkAvailability();
            var now = java.time.Instant.now();
            transcript.getChildren().add(bubble("You", text, now));
            transcript.getChildren().add(bubble("AI", answer.explanation(), now));
            transcript.getChildren().add(EvidenceView.render(answer.evidence()));
            transcript.getChildren().add(EvidenceActions.createFindingButton(study, answer.evidence(),
                    analysisService, findingService, async, errors));
        }, failure -> {
            progress.setText("");
            askBusy.finish();
            checkAvailability();
            if (failure instanceof ValidationException issue) status.setText(String.join(" ", issue.errors().values()));
            else if (failure instanceof LlmException llmFailure) status.setText(describe(llmFailure));
            else errors.accept(failure);
        });
        askBusy.start(task, () -> {
            progress.setText("");
            ask.setDisable(false);
        });
    }

    private static String describe(LlmException failure) {
        return switch (failure.kind()) {
            case TIMEOUT -> "The local AI runtime took too long to respond. It may still be loading the model "
                    + "into memory — try again in a moment, or increase RESEARCHFLOW_AI_TIMEOUT_SECONDS.";
            case UNAVAILABLE -> failure.getMessage() + " Manual analysis still works from the Run analysis tab.";
            case MALFORMED_RESPONSE -> "The AI's response could not be understood: " + failure.getMessage()
                    + " Try rephrasing the question.";
        };
    }

    private static String speakerFor(ChatRole role) {
        return role == ChatRole.USER ? "You" : "AI";
    }

    private static Parent bubble(String speaker, String content, java.time.Instant timestamp) {
        var header = new Label(speaker + " · " + TIMESTAMP.format(timestamp));
        header.getStyleClass().add("muted");
        var body = new Label(content);
        body.setWrapText(true);
        var box = new VBox(3, header, body);
        box.getStyleClass().add(speaker.equals("You") ? "chat-user" : "chat-assistant");
        box.setPadding(new Insets(10));
        return box;
    }
}
