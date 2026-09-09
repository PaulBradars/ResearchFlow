package researchflow.ai;

/**
 * Adapter over a local, HTTP-accessible LLM runtime. The application must remain fully useful
 * (manual analysis, dataset, quality, versions) when no implementation is reachable — callers
 * check {@link #isAvailable()} before depending on a response.
 */
public interface LlmClient {
    /** A fast health check; must never throw and must return promptly. */
    boolean isAvailable();

    /** The model/runtime identifier to store alongside AI-produced analyses and explanations. */
    String modelIdentifier();

    /**
     * Sends one prompt and returns the raw text completion. Throws {@link LlmException} on any
     * failure (unreachable runtime, timeout, or a non-text/error response) — never returns partial
     * or fabricated text.
     */
    String complete(String systemPrompt, String userPrompt);
}
