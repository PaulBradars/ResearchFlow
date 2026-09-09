package researchflow.service;

public final class CancellationToken {
    private final java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
    public void cancel() { cancelled.set(true); }
    public boolean isCancelled() { return cancelled.get() || Thread.currentThread().isInterrupted(); }
    public void check() { if (isCancelled()) throw new java.util.concurrent.CancellationException("Operation cancelled."); }
}
