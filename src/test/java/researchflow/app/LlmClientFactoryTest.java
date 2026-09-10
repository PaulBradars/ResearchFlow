package researchflow.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.ai.DisabledLlmClient;
import researchflow.ai.LocalLlmClient;
import researchflow.ai.LlmException;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class LlmClientFactoryTest {
    @TempDir Path directory;

    @Test void selectsDisabledClientWithoutAWorkingRuntime() {
        var client = LlmClientFactory.create(config(false));
        assertInstanceOf(DisabledLlmClient.class, client);
        assertFalse(client.isAvailable());
        assertThrows(LlmException.class, () -> client.complete("system", "question"));
    }

    @Test void constructsLocalAdapterWithoutMakingANetworkRequest() {
        var client = LlmClientFactory.create(config(true));
        assertInstanceOf(LocalLlmClient.class, client);
        assertEquals("configured-test-model", client.modelIdentifier());
        assertNotSame(client, LlmClientFactory.create(config(true)));
    }

    private AppConfig config(boolean enabled) {
        return new AppConfig(directory.resolve("test.db"), directory.resolve("logs"), false,
                enabled, "http://127.0.0.1:1", "configured-test-model", 1);
    }
}
