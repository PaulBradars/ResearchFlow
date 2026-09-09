package researchflow.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * A configurable {@link LlmClient} test double. Never touches a network — the standard test suite
 * must not depend on a real local AI runtime. Queue responses/failures in the exact order your
 * test expects {@link #complete} to be called (plan generation, then explanation, per {@code ask}).
 */
public final class FakeLlmClient implements LlmClient {
    private final Deque<Supplier<String>> queue = new ArrayDeque<>();
    private boolean available;

    private FakeLlmClient(boolean available) {
        this.available = available;
    }

    public static FakeLlmClient available() {
        return new FakeLlmClient(true);
    }

    public static FakeLlmClient unavailable() {
        return new FakeLlmClient(false);
    }

    public FakeLlmClient thenRespond(String response) {
        queue.addLast(() -> response);
        return this;
    }

    public FakeLlmClient thenFail(LlmException.Kind kind, String message) {
        queue.addLast(() -> { throw new LlmException(kind, message); });
        return this;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public String modelIdentifier() {
        return "fake-model";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (queue.isEmpty()) throw new LlmException(LlmException.Kind.MALFORMED_RESPONSE, "No fake response queued.");
        return queue.pollFirst().get();
    }
}
