package researchflow.ai;

public final class LlmException extends RuntimeException {
    public enum Kind {

        UNAVAILABLE,

        TIMEOUT,

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

