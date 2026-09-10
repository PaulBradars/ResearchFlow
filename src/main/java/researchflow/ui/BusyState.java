package researchflow.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;

import java.util.concurrent.Future;

public final class BusyState {
    private final ProgressIndicator indicator = new ProgressIndicator();
    private final Button cancelButton = new Button("Cancel");
    private final HBox box = new HBox(8, indicator, cancelButton);
    private Runnable cancelTask = () -> { };
    private Runnable onCancel = () -> { };

    public BusyState() {
        indicator.setPrefSize(18, 18);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setVisible(false);
        box.setManaged(false);
        cancelButton.setOnAction(event -> cancel());
    }

    public HBox node() {
        return box;
    }

    public void start(Future<?> task) {
        start(task, () -> { });
    }

    public void start(Future<?> task, Runnable onCancel) {
        startCancellation(() -> task.cancel(true), onCancel);
    }

    public void start(Async.TaskHandle task) { start(task, () -> { }); }

    public void start(Async.TaskHandle task, Runnable onCancel) {
        startCancellation(task::cancel, onCancel);
    }

    private void startCancellation(Runnable cancelTask, Runnable onCancel) {
        this.cancelTask = cancelTask;
        this.onCancel = onCancel;
        box.setVisible(true);
        box.setManaged(true);
    }

    public void finish() {
        cancelTask = () -> { };
        box.setVisible(false);
        box.setManaged(false);
    }

    public void cancel() {
        cancelTask.run();
        finish();
        onCancel.run();
    }
}
