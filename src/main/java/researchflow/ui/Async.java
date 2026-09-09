package researchflow.ui;

import javafx.application.Platform;
import researchflow.persistence.ConnectionFactory;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** A disposable view scope. Completion stays on the UI dispatcher; work may be cancelled cooperatively. */
public final class Async implements AutoCloseable {
    private final ExecutorService executor;
    private final Consumer<Runnable> dispatch;
    private final ConnectionFactory connections;
    private final ConcurrentMap<Object, TaskHandle> pending = new ConcurrentHashMap<>();
    private volatile boolean disposed;

    public Async(ExecutorService executor) { this(executor, Platform::runLater, null); }
    public Async(ExecutorService executor, ConnectionFactory connections) { this(executor, Platform::runLater, connections); }
    public Async(ExecutorService executor, Consumer<Runnable> dispatch, ConnectionFactory connections) {
        this.executor = executor; this.dispatch = dispatch; this.connections = connections;
    }

    public <T> TaskHandle run(Supplier<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        return submit(work.getClass(), work, success, failure);
    }

    public <T> TaskHandle submit(Object key, Supplier<T> work, Consumer<T> success, Consumer<Throwable> failure) {
        if (disposed) throw new IllegalStateException("View is disposed.");
        var handle = new TaskHandle();
        var existing = pending.putIfAbsent(key, handle);
        if (existing != null) return existing;
        handle.future = new FutureTask<Void>(() -> {
            handle.started.set(true);
            try {
                if (handle.cancelled.get()) return null;
                var value = connections == null ? work.get() : connections.inOperation(work);
                deliver(key, handle, () -> success.accept(value));
            } catch (Throwable error) { deliver(key, handle, () -> failure.accept(error));
            } finally {
                handle.finished.set(true);
                if (disposed || handle.cancelled.get()) pending.remove(key, handle);
            }
            return null;
        });
        handle.onQueuedCancel = () -> pending.remove(key, handle);
        try { executor.execute(handle.future); }
        catch (RejectedExecutionException error) { pending.remove(key, handle); throw error; }
        if (disposed) handle.cancel();
        return handle;
    }

    private void deliver(Object key, TaskHandle handle, Runnable callback) {
        dispatch.accept(() -> {
            pending.remove(key, handle);
            if (!disposed && !handle.cancelled.get()) callback.run();
        });
    }

    public TaskHandle run(Runnable work, Runnable success, Consumer<Throwable> failure) {
        return submit(work.getClass(), () -> { work.run(); return null; }, ignored -> success.run(), failure);
    }

    public boolean busy() { return !pending.isEmpty(); }
    public void update(Runnable callback) { dispatch.accept(() -> { if (!disposed) callback.run(); }); }
    public void cancelAll() { pending.values().forEach(TaskHandle::cancel); }
    @Override public void close() { disposed = true; cancelAll(); }

    public static final class TaskHandle {
        private volatile FutureTask<Void> future;
        private volatile Runnable onQueuedCancel = () -> { };
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean finished = new AtomicBoolean();
        public void cancel() {
            cancelled.set(true);
            var task = future;
            if (task != null) task.cancel(true);
            if (!started.get() || finished.get()) onQueuedCancel.run();
        }
        public boolean cancelled() { return cancelled.get(); }
        public boolean finished() { return finished.get(); }
    }
}
