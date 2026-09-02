package researchflow.app;

import java.nio.file.Path;

public record AppConfig(Path databasePath, Path logDirectory, boolean seedDevelopmentData) {
    public static AppConfig load() {
        var database = setting("researchflow.db", "RESEARCHFLOW_DB", "data/researchflow.db");
        var logs = setting("researchflow.logs", "RESEARCHFLOW_LOGS", "logs");
        var seed = Boolean.parseBoolean(setting("researchflow.seed", "RESEARCHFLOW_SEED", "true"));
        return new AppConfig(Path.of(database).toAbsolutePath().normalize(),
                Path.of(logs).toAbsolutePath().normalize(), seed);
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
