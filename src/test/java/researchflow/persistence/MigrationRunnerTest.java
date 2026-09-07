package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationRunnerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void createsAllCurrentTablesAndIsIdempotent() throws SQLException {
        var connections = TestDatabase.migrated(temporaryDirectory);
        new MigrationRunner(connections).migrate();

        try (var connection = connections.open();
             var results = connection.createStatement().executeQuery(
                     "SELECT name FROM sqlite_master WHERE type = 'table'")) {
            var names = new java.util.ArrayList<String>();
            while (results.next()) names.add(results.getString(1));
            var tables = names.stream().collect(Collectors.toSet());
            assertTrue(tables.containsAll(Set.of("studies", "research_questions", "forms", "questions",
                    "responses", "answers", "dataset_versions", "quality_issues", "analyses", "findings",
                    "audit_logs", "chat_references", "answer_corrections")));
            try (var versions = connection.createStatement().executeQuery("SELECT COUNT(*) FROM schema_migrations")) {
                versions.next(); assertEquals(3, versions.getInt(1));

            }
        }
    }

    @Test
    void enablesForeignKeysOnEveryConnection() throws SQLException {
        var connections = TestDatabase.migrated(temporaryDirectory);

        try (var connection = connections.open()) {
            try (var pragma = connection.createStatement().executeQuery("PRAGMA foreign_keys")) {
                assertTrue(pragma.next());
                assertEquals(1, pragma.getInt(1));
            }
            var failure = assertThrows(SQLException.class, () -> {
                try (var statement = connection.prepareStatement("""
                        INSERT INTO forms(id, study_id, title, status, created_at, updated_at)
                        VALUES ('form-1', 'missing-study', 'Invalid', 'DRAFT', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                        """)) {
                    statement.executeUpdate();
                }
            });
            assertTrue(failure.getMessage().toLowerCase().contains("foreign key"));
        }
    }
}
