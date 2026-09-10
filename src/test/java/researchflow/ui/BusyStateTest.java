package researchflow.ui;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Main's per-action controls must work with prottoy's scoped task handles, not bypass their cancellation. */
@EnabledOnOs(OS.WINDOWS)
class BusyStateTest {
    @BeforeAll static void toolkit() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        fx(() -> { Platform.setImplicitExit(false); return null; });
    }

    @Test void cancelHidesProgressAndSuppressesQueuedScopedCallback() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var callbacks = new LinkedBlockingQueue<Runnable>();
            try (var scope = new Async(executor, callbacks::add, null)) {
                var delivered = new AtomicInteger();
                var cancelled = new AtomicInteger();
                var task = scope.submit("merged-action", () -> 1, value -> delivered.incrementAndGet(),
                        failure -> delivered.incrementAndGet());
                var callback = callbacks.poll(5, TimeUnit.SECONDS);
                assertNotNull(callback);
                fx(() -> {
                    var busy = new BusyState();
                    busy.start(task, cancelled::incrementAndGet);
                    assertTrue(busy.node().isVisible());
                    busy.cancel();
                    assertTrue(task.cancelled());
                    assertFalse(busy.node().isVisible());
                    assertFalse(busy.node().isManaged());
                    callback.run();
                    return null;
                });
                assertEquals(1, cancelled.get());
                assertEquals(0, delivered.get());
                assertFalse(scope.busy());
            }
        }
    }

    @Test void legacyFutureCancellationRemainsSupported() throws Exception {
        fx(() -> {
            var task = new FutureTask<>(() -> 1);
            var busy = new BusyState();
            busy.start(task);
            busy.cancel();
            assertTrue(task.isCancelled());
            assertFalse(busy.node().isVisible());
            return null;
        });
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS);
    }
}
