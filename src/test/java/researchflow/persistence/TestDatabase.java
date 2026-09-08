package researchflow.persistence;

import java.nio.file.Path;

final class TestDatabase {
    private TestDatabase() {
    }

    static ConnectionFactory migrated(Path directory) {
        var connections = new ConnectionFactory(directory.resolve("researchflow-test.db"));
        new MigrationRunner(connections).migrate();
        return connections;
    }
}

