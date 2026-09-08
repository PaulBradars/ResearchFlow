package researchflow.ui;

import javafx.scene.Parent;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import researchflow.domain.Study;
import researchflow.service.FindingService;
import researchflow.service.ReportService;

import java.util.function.Consumer;

public final class FindingsReportWorkspaceView {
    private final TabPane root;

    public FindingsReportWorkspaceView(Study study, FindingService findings, ReportService report,
                                       Async async, Consumer<Throwable> errors) {
        var findingsTab = new Tab("Findings", new FindingsWorkspaceView(study, findings, async, errors).node());
        var reportTab = new Tab("Report", new ReportWorkspaceView(study, report, async, errors).node());
        root = new TabPane(findingsTab, reportTab);
        root.getTabs().forEach(tab -> tab.setClosable(false));
    }

    public Parent node() {
        return root;
    }
}

