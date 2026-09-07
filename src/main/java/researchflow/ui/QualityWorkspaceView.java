package researchflow.ui;

import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import researchflow.domain.Study;
import researchflow.service.DatasetService;
import researchflow.service.QualityReviewService;
import researchflow.service.QualityService;
import researchflow.service.VersionService;

import java.util.function.Consumer;

/** Quality / Versions workspace: the issue queue and the dataset version history, as tabs. */
public final class QualityWorkspaceView {
    private final TabPane root;

    public QualityWorkspaceView(Study study, QualityService quality, QualityReviewService review,
                                VersionService versions, DatasetService datasets, Async async, Consumer<Throwable> errors) {
        var issues = new Tab("Issues", new QualityIssuesView(study, quality, review, datasets, async, errors).node());
        var history = new Tab("Versions", new DatasetVersionsView(study, versions, async, errors).node());
        root = new TabPane(issues, history);
        root.getTabs().forEach(tab -> tab.setClosable(false));
    }

    public Parent node() {
        return root;
    }
}
