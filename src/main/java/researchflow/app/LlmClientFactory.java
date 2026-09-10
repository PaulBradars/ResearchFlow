package researchflow.app;

import researchflow.ai.LlmClient;
import researchflow.ai.LocalLlmClient;
import researchflow.ai.DisabledLlmClient;

import java.time.Duration;
import java.util.Objects;

/** Simple Factory: selects and constructs the configured adapter without probing the network. */
public final class LlmClientFactory {
    private LlmClientFactory() { }

    public static LlmClient create(AppConfig config) {
        Objects.requireNonNull(config, "config");
        return config.aiEnabled()
                ? new LocalLlmClient(config.aiBaseUrl(), config.aiModel(), Duration.ofSeconds(config.aiTimeoutSeconds()))
                : new DisabledLlmClient();
    }
}
