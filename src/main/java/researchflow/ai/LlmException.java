package researchflow.ai;

/** A clearly categorized AI-infrastructure failure — never a signal that the model computed something wrong. */
public final class LlmException extends RuntimeException {
    public enum Kind {
        /** The runtime could not be reached at all (disabled, offline, connection refused, non-2xx status). */
        UNAVAILABLE,
        /** The runtime was reached but did not respond within the configured timeout. */
        TIMEOUT,
        /** A response was received but could not be defensively parsed into a usable plan or text. */
        MALFORMED_RESPONSE
    }

    private final Kind kind;

    public LlmException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public LlmException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
