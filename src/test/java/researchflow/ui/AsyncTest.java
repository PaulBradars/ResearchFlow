package researchflow.ui;
import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class AsyncTest {
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
