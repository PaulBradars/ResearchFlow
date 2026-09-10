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
    private final Button clear = new Button("Clear chat");
    private final Button save = new Button("Save chat history");
    private boolean busy;
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
        question.setOnAction(event -> { if (!busy) ask(); });
        clear.setOnAction(event -> clearHistory());
        save.setOnAction(event -> saveHistory());
        var bar = new HBox(8, question, ask);
        HBox.setHgrow(question, Priority.ALWAYS);

        status.getStyleClass().add("field-error");
        status.setWrapText(true);
        progress.getStyleClass().add("muted");
        progress.setWrapText(true);
        transcript.setPadding(new Insets(4));
        var scroll = new ScrollPane(transcript);
        scroll.setFitToWidth(true);

        var hint = new Label("Chat naturally, ask for help, or analyze your data. History is saved automatically; export a copy below.");
        hint.setWrapText(true);
        root.getChildren().addAll(eyebrow, title, hint, bar, new HBox(8, clear, save), status, progress, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        checkAvailability();
        refresh();
    }

    public Parent node() {
        return root;
    }

    private void checkAvailability() {
        async.run(facade::aiAvailable, available -> {
            status.setText(available ? ""
                    : "Local AI is unavailable. Basic greetings still work; start the local runtime for other questions.");
        }, errors);
    }

    private void refresh() {
        setBusy(true);
        async.run(() -> facade.history(study.id()), messages -> {
            showHistory(messages);
            setBusy(false);
        }, failure -> { setBusy(false); errors.accept(failure); });
    }

    private void setBusy(boolean value) {
        busy = value;
        ask.setDisable(value);
        question.setDisable(value);
        clear.setDisable(value);
        save.setDisable(value);
    }

    private void clearHistory() {
        var confirmation = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION,
                "Clear this Study's chat history? Saved analyses and findings will remain. Export a copy first if needed.",
                javafx.scene.control.ButtonType.CANCEL, javafx.scene.control.ButtonType.OK);
        confirmation.initOwner(root.getScene().getWindow());
        confirmation.setHeaderText("Clear chat history");
        if (confirmation.showAndWait().orElse(javafx.scene.control.ButtonType.CANCEL)
                != javafx.scene.control.ButtonType.OK) return;
        setBusy(true);
        async.run(() -> facade.clearHistory(study.id()), () -> {
            transcript.getChildren().clear();
            status.setText("");
            progress.setText("Chat history cleared.");
            setBusy(false);
        }, failure -> { setBusy(false); errors.accept(failure); });
    }

    private void saveHistory() {
        var chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Save chat history");
        chooser.setInitialFileName("researchflow-chat-" + java.time.LocalDate.now() + ".txt");
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Text files", "*.txt"));
        var file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file == null) return;
        setBusy(true);
        async.run(() -> {
            try {
                java.nio.file.Files.writeString(file.toPath(), facade.exportHistory(study.id()),
                        java.nio.charset.StandardCharsets.UTF_8);
            } catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }, () -> {
            progress.setText("Chat history saved to " + file.getAbsolutePath());
            setBusy(false);
        }, failure -> { setBusy(false); errors.accept(failure); });
    }

    private void showHistory(List<ChatMessage> messages) {
        transcript.getChildren().clear();
        for (var message : messages) {
            transcript.getChildren().add(bubble(speakerFor(message.role()), message.content(), message.createdAt()));
        }
    }

    private void ask() {
        if (busy || question.getText().isBlank()) return;
        var text = question.getText();
        status.setText("");
        setBusy(true);
        progress.setText("Thinking… a local model can take up to a minute, especially on the first "
                + "request while it loads.");
        async.run(() -> facade.ask(study.id(), text), answer -> {
            progress.setText("");
            question.clear();
            setBusy(false);
            var now = java.time.Instant.now();
            transcript.getChildren().add(bubble("You", text, now));
            transcript.getChildren().add(bubble("AI", answer.explanation(), now));
            if (answer.evidence() != null) {
                transcript.getChildren().add(EvidenceActions.createFindingButton(study, answer.evidence(),
                        analysisService, findingService, async, errors));
            }
        }, failure -> {
            progress.setText("");
            setBusy(false);
            if (failure instanceof ValidationException issue) status.setText(String.join(" ", issue.errors().values()));
            else if (failure instanceof LlmException llmFailure) status.setText(describe(llmFailure));
            else errors.accept(failure);
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
        var marker = "\n\nStatistical breakdown\n";
        int breakdownAt = content.indexOf(marker);
        var body = new Label(breakdownAt < 0 ? content : content.substring(0, breakdownAt));
        body.setWrapText(true);
        var box = new VBox(3, header, body);
        if (breakdownAt >= 0) {
            var details = new VBox(6);
            details.setPadding(new Insets(10));
            for (var line : content.substring(breakdownAt + marker.length()).split("\n")) {
                var label = new Label(line);
                label.setWrapText(true);
                if (List.of("Descriptive statistics on the same paired rows", "Interpretation limits",
                        "Data quality and limitations").contains(line)) label.getStyleClass().add("question-label");
                details.getChildren().add(label);
            }
            var panel = new javafx.scene.control.TitledPane("Computed statistical breakdown", details);
            panel.setExpanded(true);
            panel.setAnimated(false);
            box.getChildren().add(panel);
        }
        box.getStyleClass().add(speaker.equals("You") ? "chat-user" : "chat-assistant");
        box.setPadding(new Insets(10));
        return box;
    }
}
