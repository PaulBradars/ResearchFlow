package researchflow.ai;

public final class DisabledLlmClient implements LlmClient {
    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String modelIdentifier() {
        return "disabled";
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        throw new LlmException(LlmException.Kind.UNAVAILABLE, "The local AI runtime is disabled in configuration.");
    }
}
