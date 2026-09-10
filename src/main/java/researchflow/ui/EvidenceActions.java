package researchflow.ui;

import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import researchflow.domain.EvidenceBundle;
import researchflow.domain.Study;
import researchflow.service.AnalysisService;
import researchflow.service.FindingService;
import researchflow.service.ValidationException;
import researchflow.visualization.ChartBuilder;

import java.util.function.Consumer;

final class EvidenceActions {
    private EvidenceActions() { }

    static Node createFindingButton(Study study, EvidenceBundle bundle, AnalysisService analysisService,
                                    FindingService findingService, Async async, Consumer<Throwable> errors) {
        var button = new Button("Create finding from this evidence");
        button.setOnAction(event -> {
            button.setDisable(true);
            async.run(() -> analysisService.chartValues(study.id(), bundle), values -> {
                button.setDisable(false);
                var chart = ChartBuilder.build(bundle, values.primary(), values.secondary()).orElse(null);
                FindingEditorDialog.create(FindingService.draftText(bundle), chart).showAndWait().ifPresent(text ->
                        async.run(() -> findingService.draft(study.id(), bundle, text, chart), ignored -> {
                            var confirm = new Alert(Alert.AlertType.INFORMATION,
                                    "Finding saved as a draft. Approve it from the Findings / Report workspace.", ButtonType.OK);
                            confirm.setHeaderText("Finding created");
                            confirm.showAndWait();
                        }, failure -> {
                            if (failure instanceof ValidationException issue) {
                                var alert = new Alert(Alert.AlertType.ERROR, String.join(" ", issue.errors().values()), ButtonType.OK);
                                alert.setHeaderText("The finding was not saved");
                                alert.showAndWait();
                            } else errors.accept(failure);
                        }));
            }, failure -> {
                button.setDisable(false);
                errors.accept(failure);
            });
        });
        return button;
    }
}
