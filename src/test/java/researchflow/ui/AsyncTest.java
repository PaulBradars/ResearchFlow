package researchflow.ui;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class AsyncTest {
    @Test void cancelledActionCanBeRetriedWhileItsWorkerUnwinds() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var callbacks = new LinkedBlockingQueue<Runnable>();
            try (var scope = new Async(executor, callbacks::add, null)) {
                var entered = new CountDownLatch(1);
                var release = new CountDownLatch(1);
                var values = new AtomicInteger();
                var first = scope.submit("retry", () -> {
                    entered.countDown();
                    boolean interrupted = false;
                    while (true) {
                        try { release.await(); break; }
                        catch (InterruptedException ignored) { interrupted = true; }
                    }
                    if (interrupted) Thread.currentThread().interrupt();
                    return 1;
                }, values::set, e -> fail(e));
                try {
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    first.cancel();
                    var retry = scope.submit("retry", () -> 2, values::set, e -> fail(e));
                    assertNotSame(first, retry);
                    assertFalse(retry.cancelled());
                } finally { release.countDown(); }
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (scope.busy() && System.nanoTime() < deadline) {
                    var callback = callbacks.poll(1, TimeUnit.SECONDS);
                    if (callback != null) callback.run();
                }
                assertEquals(2, values.get());
                assertFalse(scope.busy());
            }
        }
    }
    @Test void duplicateActionRunsOnceAndDisposedViewReceivesNoCallback() throws Exception {
        try(var executor = Executors.newSingleThreadExecutor()) {
            var callbacks = new LinkedBlockingQueue<Runnable>(); var scope = new Async(executor, callbacks::add, null);
            var entered = new CountDownLatch(1); var release = new CountDownLatch(1); var delivered = new AtomicInteger();
            var first = scope.submit("analysis", () -> { entered.countDown(); try { release.await(); } catch(InterruptedException e) { Thread.currentThread().interrupt(); } return 1; }, ignored -> delivered.incrementAndGet(), ignored -> delivered.incrementAndGet());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertSame(first, scope.submit("analysis", () -> 2, ignored -> delivered.incrementAndGet(), ignored -> delivered.incrementAndGet()));
            release.countDown(); var callback = callbacks.poll(5, TimeUnit.SECONDS); assertNotNull(callback);
            scope.close(); callback.run(); assertEquals(0, delivered.get());
        }
    }
    @Test void callbackRunsOnlyThroughDispatcher() throws Exception {
        try(var executor = Executors.newSingleThreadExecutor()) {
            var callbacks = new LinkedBlockingQueue<Runnable>(); var scope = new Async(executor, callbacks::add, null); var count = new AtomicInteger();
            scope.submit("export", () -> 1, count::set, e -> fail(e));
            var callback = callbacks.poll(5, TimeUnit.SECONDS); assertNotNull(callback); assertEquals(0,count.get());
            callback.run(); assertEquals(1,count.get()); assertFalse(scope.busy()); scope.close();
        }
    }
}
