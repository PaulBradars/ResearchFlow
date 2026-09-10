package researchflow.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class LocalLlmClient implements LlmClient {
    private final HttpClient http;
    private final URI generateEndpoint;
    private final URI tagsEndpoint;
    private final String configuredModel;
    private final Duration timeout;
    private volatile String resolvedModel;

    public LocalLlmClient(String baseUrl, String model, Duration timeout) {
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        var normalizedBase = stripTrailingSlash(baseUrl);
        this.generateEndpoint = URI.create(normalizedBase + "/api/generate");
        this.tagsEndpoint = URI.create(normalizedBase + "/api/tags");
        this.configuredModel = model == null ? "" : model.strip();
        this.timeout = timeout;
    }

    @Override
    public boolean isAvailable() {
        try {
            return !fetchInstalledModels().isEmpty();
        } catch (Exception exception) {
            return false;
        }
    }

    @Override
    public String modelIdentifier() {
        if (resolvedModel != null) return resolvedModel;
        return configuredModel.isBlank() ? "auto" : configuredModel;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        var model = resolveModel();
        var body = "{\"model\":\"" + SimpleJson.escape(model) + "\",\"system\":\"" + SimpleJson.escape(systemPrompt)
                + "\",\"prompt\":\"" + SimpleJson.escape(userPrompt) + "\",\"stream\":false}";
        var request = HttpRequest.newBuilder(generateEndpoint).timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new LlmException(LlmException.Kind.UNAVAILABLE,
                        "The local AI runtime returned status " + response.statusCode() + ".");
            }
            var text = SimpleJson.stringField(response.body(), "response");
            if (text == null) {
                throw new LlmException(LlmException.Kind.MALFORMED_RESPONSE,
                        "The local AI runtime returned an unexpected response shape.");
            }
            return text;
        } catch (HttpTimeoutException exception) {
            throw new LlmException(LlmException.Kind.TIMEOUT, "The local AI runtime did not respond in time.", exception);
        } catch (IOException exception) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE, "The local AI runtime is not reachable.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.Kind.UNAVAILABLE, "The AI request was cancelled.", exception);
        }
    }

    private String resolveModel() {
        var cached = resolvedModel;
        if (cached != null) return cached;

        List<String> installed;
        try {
            installed = fetchInstalledModels();
        } catch (HttpTimeoutException exception) {
            throw new LlmException(LlmException.Kind.TIMEOUT, "The local AI runtime did not respond in time.", exception);
        } catch (IOException exception) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE, "The local AI runtime is not reachable.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LlmException(LlmException.Kind.UNAVAILABLE, "The AI request was cancelled.", exception);
        }
        if (installed.isEmpty()) {
            throw new LlmException(LlmException.Kind.UNAVAILABLE,
                    "No models are installed in the local AI runtime. Run e.g. \"ollama pull llama3.2\" "
                            + "(or any model you prefer), then try again.");
        }
        if (configuredModel.isBlank()) {
            resolvedModel = installed.getFirst();
            return resolvedModel;
        }
        var match = installed.stream()
                .filter(name -> name.equalsIgnoreCase(configuredModel) || name.startsWith(configuredModel + ":"))
                .findFirst();
        if (match.isPresent()) {
            resolvedModel = match.get();
            return resolvedModel;
        }
        throw new LlmException(LlmException.Kind.UNAVAILABLE,
                "Model \"" + configuredModel + "\" is not installed. Installed models: " + String.join(", ", installed)
                        + ". Run \"ollama pull " + configuredModel + "\", or set RESEARCHFLOW_AI_MODEL to one of the "
                        + "installed names above.");
    }

    private List<String> fetchInstalledModels() throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(tagsEndpoint).timeout(timeout).GET().build();
        var response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) return List.of();
        var modelsArray = SimpleJson.arrayField(response.body(), "models");
        if (modelsArray == null) return List.of();
        var names = new ArrayList<String>();
        for (var entry : SimpleJson.objectsIn(modelsArray)) {
            var name = SimpleJson.stringField(entry, "name");
            if (name != null) names.add(name);
        }
        return List.copyOf(names);
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
