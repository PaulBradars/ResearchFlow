package researchflow.app;

import java.nio.file.Path;

public record AppConfig(Path databasePath, Path logDirectory, boolean seedDevelopmentData,
                        boolean aiEnabled, String aiBaseUrl, String aiModel, int aiTimeoutSeconds) {
    public static AppConfig load() {
        var database = setting("researchflow.db", "RESEARCHFLOW_DB", "data/researchflow.db");
        var logs = setting("researchflow.logs", "RESEARCHFLOW_LOGS", "logs");
        var seed = Boolean.parseBoolean(setting("researchflow.seed", "RESEARCHFLOW_SEED", "true"));
        var aiEnabled = Boolean.parseBoolean(setting("researchflow.ai.enabled", "RESEARCHFLOW_AI_ENABLED", "true"));
        var aiBaseUrl = setting("researchflow.ai.baseUrl", "RESEARCHFLOW_AI_BASE_URL", "http://localhost:11434");
        // Blank (not a hardcoded name like "llama3.2"): LocalLlmClient auto-selects whichever model is
        // actually installed, so a fresh machine with any one model pulled works with zero configuration.
        var aiModel = setting("researchflow.ai.model", "RESEARCHFLOW_AI_MODEL", "");
        // 60s (not 20s): a local "thinking"/reasoning model can easily spend 15-30s per call once its
        // context is warm, and Ask Your Data makes two sequential calls (plan, then explanation).
        var aiTimeoutSeconds = Integer.parseInt(
                setting("researchflow.ai.timeoutSeconds", "RESEARCHFLOW_AI_TIMEOUT_SECONDS", "60"));
        return new AppConfig(Path.of(database).toAbsolutePath().normalize(),
                Path.of(logs).toAbsolutePath().normalize(), seed, aiEnabled, aiBaseUrl, aiModel, aiTimeoutSeconds);
    }

    private static String setting(String property, String environment, String fallback) {
        var fromProperty = System.getProperty(property);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        var fromEnvironment = System.getenv(environment);
        return fromEnvironment == null || fromEnvironment.isBlank() ? fallback : fromEnvironment;
    }
}
