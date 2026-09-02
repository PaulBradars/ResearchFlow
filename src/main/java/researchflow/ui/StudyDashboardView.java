package researchflow.ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import researchflow.domain.Study;
import researchflow.domain.StudyMetrics;
import researchflow.service.StudyService;

import java.util.function.Consumer;

public final class StudyDashboardView {
    private final VBox root = new VBox(18);
    private final FlowPane metrics = new FlowPane(12, 12);

    public StudyDashboardView(Study study, StudyService studies, Async async, Consumer<Throwable> errors) {
        root.setPadding(new Insets(28));
        var eyebrow = new Label("STUDY DASHBOARD");
        eyebrow.getStyleClass().add("eyebrow");
        var title = new Label(study.title());
        title.getStyleClass().add("page-title");
        var description = new Label(study.description().isBlank() ? "No description added yet." : study.description());
        description.setWrapText(true);
        description.getStyleClass().add("muted");
        metrics.getChildren().add(new Label("Loading study summary…"));

        var objectivesTitle = new Label("Objectives");
        objectivesTitle.getStyleClass().add("section-title");
        var objectives = new Label(study.objectives().isBlank() ? "No objectives added yet." : study.objectives());
        objectives.setWrapText(true);
        var questionsTitle = new Label("Research questions");
        questionsTitle.getStyleClass().add("section-title");
        var questions = new VBox(7);
        if (study.researchQuestions().isEmpty()) {
            questions.getChildren().add(new Label("No research questions added yet."));
        } else {
            for (int index = 0; index < study.researchQuestions().size(); index++) {
                questions.getChildren().add(new Label((index + 1) + ". " + study.researchQuestions().get(index)));
            }
        }
        root.getChildren().addAll(eyebrow, title, description, metrics, objectivesTitle, objectives,
                questionsTitle, questions);
        async.run(() -> studies.metrics(study.id()), this::showMetrics, errors);
    }

    public Parent node() {
        return root;
    }

    private void showMetrics(StudyMetrics values) {
        metrics.getChildren().setAll(
                card("Forms", values.forms()), card("Responses", values.responses()),
                card("Open quality issues", values.unresolvedIssues()), card("Dataset versions", values.datasetVersions()),
                card("Analyses", values.recentAnalyses()), card("Approved findings", values.approvedFindings()));
    }

    private static VBox card(String label, long value) {
        var number = new Label(Long.toString(value));
        number.getStyleClass().add("metric-number");
        var caption = new Label(label);
        caption.getStyleClass().add("muted");
        var card = new VBox(4, number, caption);
        card.getStyleClass().add("metric-card");
        card.setPrefWidth(180);
        return card;
    }
}
