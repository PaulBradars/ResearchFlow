package researchflow.ui;

import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Async {
    private final ExecutorService executor;

    public Async(ExecutorService executor) {
        this.executor = executor;
    }

    public <T> void run(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        executor.submit(() -> {
            try {
                var result = work.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable failure) {
                Platform.runLater(() -> onFailure.accept(failure));
            }
        });
    }

    public void run(Runnable work, Runnable onSuccess, Consumer<Throwable> onFailure) {
        run(() -> {
            work.run();
            return null;
        }, ignored -> onSuccess.run(), onFailure);
    }
}
