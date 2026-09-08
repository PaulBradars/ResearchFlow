package researchflow.ai;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

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

