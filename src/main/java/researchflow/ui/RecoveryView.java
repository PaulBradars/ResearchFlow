package researchflow.ui;

import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import researchflow.service.DatabaseRecoveryService;
import java.util.function.Consumer;

public final class RecoveryView {
    private final VBox root = new VBox(12);
    public RecoveryView(DatabaseRecoveryService recovery, Async async, Runnable reopened, Consumer<Boolean> recoveryBusy) {
        root.setPadding(new javafx.geometry.Insets(28));
        var status = new Label(); status.setWrapText(true);
        var backup = new Button("Create database backup");
        var restore = new Button("Restore database backup");
        var back = new Button("Back to studies"); back.setOnAction(event -> reopened.run());
        backup.setOnAction(event -> {
            var chooser = new FileChooser(); chooser.setInitialFileName("researchflow-backup.db");
            var file = chooser.showSaveDialog(root.getScene().getWindow()); if (file == null) return;
            backup.setDisable(true); restore.setDisable(true);
            async.run(() -> recovery.backup(file.toPath()), path -> {
                status.setText("Verified backup saved to " + path); backup.setDisable(false); restore.setDisable(false);
            }, failure -> { backup.setDisable(false); restore.setDisable(false); status.setText(failure.getMessage()); });
        });
        restore.setOnAction(event -> {
            var chooser = new FileChooser(); var file = chooser.showOpenDialog(root.getScene().getWindow()); if (file == null) return;
            var confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Restore all studies from this backup? The current database will first be saved as a safety backup.", ButtonType.CANCEL, ButtonType.OK);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            backup.setDisable(true); restore.setDisable(true); back.setDisable(true);
            recoveryBusy.accept(true);
            async.run(() -> recovery.restore(file.toPath()), safety -> {
                recoveryBusy.accept(false);
                var message = new Alert(Alert.AlertType.INFORMATION, "Recovery completed. Previous database saved to " + safety, ButtonType.OK);
                message.showAndWait(); reopened.run();
            }, failure -> { recoveryBusy.accept(false); backup.setDisable(false); restore.setDisable(false); back.setDisable(false); status.setText(failure.getMessage()); });
        });
        root.getChildren().addAll(new Label("Database backup and verified recovery"),
                new Label("Recovery replaces the entire database. Old views are discarded before recovery begins."), backup, restore, back, status);
    }
    public Parent node() { return root; }
}
