package researchflow.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.List;

public final class MigrationRunner {
    private static final List<String> MIGRATIONS = List.of(
            "db/migration/V001__initial_schema.sql",
            "db/migration/V002__dataset_audit_baseline.sql"
    );
    private final ConnectionFactory connections;

    public MigrationRunner(ConnectionFactory connections) {
        this.connections = connections;
    }

    public void migrate() {
        try (var connection = connections.open()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS schema_migrations (
                            version TEXT PRIMARY KEY,
                            applied_at TEXT NOT NULL
                        )
                        """);
                for (var resource : MIGRATIONS) {
                    var version = resource.substring(resource.lastIndexOf('/') + 1, resource.indexOf("__"));
                    if (!isApplied(connection, version)) {
                        executeScript(connection, readResource(resource));
                        try (var insert = connection.prepareStatement(
                                "INSERT INTO schema_migrations(version, applied_at) VALUES (?, CURRENT_TIMESTAMP)")) {
                            insert.setString(1, version);
                            insert.executeUpdate();
                        }
                    }
                }
                connection.commit();
            } catch (SQLException | IOException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException | IOException exception) {
            throw new PersistenceException("Database migration failed.", exception);
        }
    }

    private static boolean isApplied(java.sql.Connection connection, String version) throws SQLException {
        try (var query = connection.prepareStatement("SELECT 1 FROM schema_migrations WHERE version = ?")) {
            query.setString(1, version);
            try (var results = query.executeQuery()) {
                return results.next();
            }
        }
    }

    private static String readResource(String path) throws IOException {
        var stream = MigrationRunner.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IOException("Missing migration resource: " + path);
        }
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void executeScript(java.sql.Connection connection, String script) throws SQLException {
        for (var statementText : script.split(";")) {
            var sql = statementText.strip();
            if (!sql.isEmpty()) {
                try (var statement = connection.createStatement()) {
                    statement.execute(sql);
                }
            }
        }
    }
}
