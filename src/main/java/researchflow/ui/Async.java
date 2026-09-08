package researchflow.ui;

import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class Async {
    private final ExecutorService executor;

    public Async(ExecutorService executor) {
        this.executor = executor;
    }

    public <T> Future<?> run(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        return executor.submit(() -> {
            try {
                var result = work.get();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable failure) {
                Platform.runLater(() -> onFailure.accept(failure));
            }
        });
    }

    public Future<?> run(Runnable work, Runnable onSuccess, Consumer<Throwable> onFailure) {
        return run(() -> {
            work.run();
            return null;
        }, ignored -> onSuccess.run(), onFailure);
    }
}

