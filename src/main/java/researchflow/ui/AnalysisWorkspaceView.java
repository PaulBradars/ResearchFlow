package researchflow.ui;

import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import researchflow.domain.Study;
import researchflow.service.AnalysisFacade;
import researchflow.service.AnalysisService;
import researchflow.service.DatasetService;
import researchflow.service.FindingService;
import researchflow.service.VersionService;

import java.util.function.Consumer;

public final class AnalysisWorkspaceView {
    private final TabPane root;

    public AnalysisWorkspaceView(Study study, AnalysisService analysis, AnalysisFacade aiFacade, FindingService findings,
                                 DatasetService datasets, VersionService versions, Async async, Consumer<Throwable> errors) {
        var run = new Tab("Run analysis",
                new AnalysisRunView(study, analysis, findings, datasets, versions, async, errors).node());
        var ask = new Tab("Ask Your Data",
                new AskYourDataView(study, aiFacade, analysis, findings, async, errors).node());
        var history = new Tab("History", new AnalysisHistoryView(study, analysis, async, errors).node());
        root = new TabPane(run, ask, history);
        root.getTabs().forEach(tab -> tab.setClosable(false));
    }

    public Parent node() {
        return root;
    }
}

