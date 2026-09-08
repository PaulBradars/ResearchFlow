package researchflow.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLlmClientTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void completesUsingAnExplicitlyConfiguredModelThatIsInstalled() throws IOException {
        server = stubServer("[\"llama3.2\"]", "{\"response\":\"hello there\"}", 200, 200);
        var client = new LocalLlmClient(baseUrl(server), "llama3.2", Duration.ofSeconds(5));

        assertTrue(client.isAvailable());
        assertEquals("hello there", client.complete("system", "user"));
        assertEquals("llama3.2", client.modelIdentifier());
    }

    @Test
    void autoSelectsTheFirstInstalledModelWhenNoneIsConfigured() throws IOException {
        server = stubServer("[\"qwen3:8b\",\"llama3.2\"]", "{\"response\":\"ok\"}", 200, 200);
        var client = new LocalLlmClient(baseUrl(server), "", Duration.ofSeconds(5));

        assertEquals("ok", client.complete("system", "user"));
        assertEquals("qwen3:8b", client.modelIdentifier());
    }

    @Test
    void matchesAConfiguredBaseNameAgainstATaggedInstalledModel() throws IOException {
        server = stubServer("[\"qwen3:8b\"]", "{\"response\":\"ok\"}", 200, 200);
        var client = new LocalLlmClient(baseUrl(server), "qwen3", Duration.ofSeconds(5));

        assertEquals("ok", client.complete("system", "user"));
        assertEquals("qwen3:8b", client.modelIdentifier());
    }

    @Test
    void rejectsAConfiguredModelThatIsNotInstalledWithAMessageListingWhatIs() throws IOException {
        server = stubServer("[\"llama3.2\"]", "{\"response\":\"ok\"}", 200, 200);
        var client = new LocalLlmClient(baseUrl(server), "mistral", Duration.ofSeconds(5));

        var exception = assertThrows(LlmException.class, () -> client.complete("system", "user"));
        assertEquals(LlmException.Kind.UNAVAILABLE, exception.kind());
        assertTrue(exception.getMessage().contains("mistral"));
        assertTrue(exception.getMessage().contains("llama3.2"));
    }

    @Test
    void reportsUnavailableWhenNoModelsAreInstalledAtAll() throws IOException {
        server = stubServer("[]", "{\"response\":\"ok\"}", 200, 200);
        var client = new LocalLlmClient(baseUrl(server), "", Duration.ofSeconds(5));

        assertFalse(client.isAvailable());
        var exception = assertThrows(LlmException.class, () -> client.complete("system", "user"));
        assertEquals(LlmException.Kind.UNAVAILABLE, exception.kind());
    }

    @Test
    void mapsANonTwoHundredGenerateStatusToUnavailable() throws IOException {
        server = stubServer("[\"llama3.2\"]", "{\"error\":\"boom\"}", 200, 500);
        var client = new LocalLlmClient(baseUrl(server), "llama3.2", Duration.ofSeconds(5));

        var exception = assertThrows(LlmException.class, () -> client.complete("system", "user"));
        assertEquals(LlmException.Kind.UNAVAILABLE, exception.kind());
    }

    @Test
    void mapsAResponseWithNoResponseFieldToMalformed() throws IOException {
        server = stubServer("[\"llama3.2\"]", "{\"unexpected\":true}", 200, 200);
        var client = new LocalLlmClient(baseUrl(server), "llama3.2", Duration.ofSeconds(5));

        var exception = assertThrows(LlmException.class, () -> client.complete("system", "user"));
        assertEquals(LlmException.Kind.MALFORMED_RESPONSE, exception.kind());
    }

    @Test
    void reportsUnavailableWhenNothingIsListening() {
        var client = new LocalLlmClient("http://localhost:1", "llama3.2", Duration.ofSeconds(2));
        assertFalse(client.isAvailable());
    }

    private static String baseUrl(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static HttpServer stubServer(String modelNamesJsonArray, String generateBody, int tagsStatus,
                                         int generateStatus) throws IOException {
        var server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/tags", exchange -> {
            var body = ("{\"models\":" + toModelObjects(modelNamesJsonArray) + "}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(tagsStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/api/generate", exchange -> {
            exchange.getRequestBody().readAllBytes();
            var body = generateBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(generateStatus, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String toModelObjects(String namesJsonArray) {
        var inner = namesJsonArray.strip();
        inner = inner.substring(1, inner.length() - 1).strip();
        if (inner.isEmpty()) return "[]";
        var builder = new StringBuilder("[");
        var names = inner.split(",");
        for (int index = 0; index < names.length; index++) {
            if (index > 0) builder.append(',');
            builder.append("{\"name\":").append(names[index].strip()).append('}');
        }
        return builder.append(']').toString();
    }
}

