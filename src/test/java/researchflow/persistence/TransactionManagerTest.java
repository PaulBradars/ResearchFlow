package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransactionManagerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rollsBackAllWritesWhenWorkFails() throws Exception {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);

        assertThrows(IllegalStateException.class, () -> transactions.inTransaction(connection -> {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO studies(id, title, status, created_at, updated_at)
                    VALUES ('study-rollback', 'Rollback', 'ACTIVE', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
                    """)) {
                statement.executeUpdate();
            }
            throw new IllegalStateException("forced failure");
        }));

        try (var connection = connections.open();
             var results = connection.createStatement().executeQuery(
                     "SELECT COUNT(*) FROM studies WHERE id = 'study-rollback'")) {
            results.next();
            assertEquals(0, results.getInt(1));
        }
    }
}
