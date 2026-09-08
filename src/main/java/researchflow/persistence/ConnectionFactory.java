package researchflow.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class ConnectionFactory {
    private final String jdbcUrl;

    public ConnectionFactory(Path databasePath) {
        try {
            var parent = databasePath.toAbsolutePath().normalize().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            this.jdbcUrl = "jdbc:sqlite:" + databasePath.toAbsolutePath().normalize();
        } catch (IOException exception) {
            throw new PersistenceException("Could not create the database directory.", exception);
        }
    }

    public ConnectionFactory(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:sqlite:")) {
            throw new IllegalArgumentException("A SQLite JDBC URL is required.");
        }
        this.jdbcUrl = jdbcUrl;
    }

    public Connection open() {
        try {
            var connection = DriverManager.getConnection(jdbcUrl);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");

                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA synchronous = NORMAL");
            }
            return connection;
        } catch (SQLException exception) {
            throw new PersistenceException("Could not open the local database.", exception);
        }
    }
}

