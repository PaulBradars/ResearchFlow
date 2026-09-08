package researchflow.ai;

public interface LlmClient {

    boolean isAvailable();

    String modelIdentifier();

    String complete(String systemPrompt, String userPrompt);
}

