package researchflow.app;

import java.nio.file.Path;

public record AppConfig(Path databasePath, Path logDirectory, boolean seedDevelopmentData,
                        boolean aiEnabled, String aiBaseUrl, String aiModel, int aiTimeoutSeconds) {
    public AppConfig {
        if (databasePath == null || logDirectory == null) throw new IllegalArgumentException("Configure database and log paths.");
        databasePath = databasePath.toAbsolutePath().normalize();
        logDirectory = logDirectory.toAbsolutePath().normalize();
        if (logDirectory.startsWith(databasePath)) throw new IllegalArgumentException("The log directory must be separate from the database file.");
        if (java.nio.file.Files.isDirectory(databasePath)) throw new IllegalArgumentException("researchflow.db must name a file, not a directory.");
        if (aiTimeoutSeconds < 1 || aiTimeoutSeconds > 3600) throw new IllegalArgumentException("researchflow.ai.timeoutSeconds must be between 1 and 3600.");
        try {
            var uri = java.net.URI.create(aiBaseUrl);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || uri.getPort() > 65535) throw new IllegalArgumentException();
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("researchflow.ai.baseUrl must be an HTTP(S) endpoint without credentials, query, or fragment."); }
        aiModel = aiModel == null ? "" : aiModel.strip();
        if (aiModel.chars().anyMatch(Character::isWhitespace) || aiModel.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("researchflow.ai.model must be a model identifier, or blank for automatic selection.");
        try {
            java.nio.file.Files.createDirectories(databasePath.getParent());
            java.nio.file.Files.createDirectories(logDirectory);
            if (!java.nio.file.Files.isWritable(databasePath.getParent()) || !java.nio.file.Files.isWritable(logDirectory)
                    || (java.nio.file.Files.exists(databasePath) && !java.nio.file.Files.isWritable(databasePath))) throw new java.io.IOException();
        } catch (java.io.IOException invalid) { throw new IllegalArgumentException("Configure writable database and log directories.", invalid); }
    }

    public String aiDiagnostics() { return "AI " + (aiEnabled ? "enabled" : "disabled") + " | endpoint=" + aiBaseUrl
            + " | model=" + (aiModel.isBlank() ? "automatic selection" : aiModel) + " | timeout=" + aiTimeoutSeconds + "s"; }

    public static AppConfig load() {
        var database = setting("researchflow.db", "RESEARCHFLOW_DB", "data/researchflow.db");
        var logs = setting("researchflow.logs", "RESEARCHFLOW_LOGS", "logs");
        var seed = flag("researchflow.seed", "RESEARCHFLOW_SEED", "true");
        var aiEnabled = flag("researchflow.ai.enabled", "RESEARCHFLOW_AI_ENABLED", "true");
        var aiBaseUrl = setting("researchflow.ai.baseUrl", "RESEARCHFLOW_AI_BASE_URL", "http://localhost:11434");
        // Blank (not a hardcoded name like "llama3.2"): LocalLlmClient auto-selects whichever model is
        // actually installed, so a fresh machine with any one model pulled works with zero configuration.
        var aiModel = setting("researchflow.ai.model", "RESEARCHFLOW_AI_MODEL", "");
        // 60s (not 20s): a local "thinking"/reasoning model can easily spend 15-30s per call once its
        // context is warm, and Ask Your Data makes two sequential calls (plan, then explanation).
        final int aiTimeoutSeconds;
        try { aiTimeoutSeconds = Integer.parseInt(setting("researchflow.ai.timeoutSeconds", "RESEARCHFLOW_AI_TIMEOUT_SECONDS", "60")); }
        catch (NumberFormatException invalid) { throw new IllegalArgumentException("researchflow.ai.timeoutSeconds must be a whole number between 1 and 3600."); }
        try { return new AppConfig(Path.of(database), Path.of(logs), seed, aiEnabled, aiBaseUrl, aiModel, aiTimeoutSeconds); }
        catch (java.nio.file.InvalidPathException invalid) { throw new IllegalArgumentException("Configure valid researchflow.db and researchflow.logs paths."); }
    }

    private static String setting(String property, String environment, String fallback) {
        var fromProperty = System.getProperty(property);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        var fromEnvironment = System.getenv(environment);
        return fromEnvironment == null || fromEnvironment.isBlank() ? fallback : fromEnvironment;
    }

    private static boolean flag(String property, String environment, String fallback) {
        var value = setting(property, environment, fallback);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
            throw new IllegalArgumentException(property + " must be true or false.");
        return Boolean.parseBoolean(value);
    }
}
