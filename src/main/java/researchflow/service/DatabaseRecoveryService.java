package researchflow.service;

import org.sqlite.SQLiteConnection;
import researchflow.persistence.ConnectionFactory;
import researchflow.persistence.MigrationRunner;
import researchflow.persistence.PersistenceException;
import researchflow.util.AtomicFiles;

import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;

/** SQLite online backup, staged validation/migration, then SQLite's atomic destination restore. */
public final class DatabaseRecoveryService {
    private final ConnectionFactory connections;
    private final Path active;

    public DatabaseRecoveryService(ConnectionFactory connections, Path active) {
        this.connections = connections;
        this.active = active.toAbsolutePath().normalize();
    }

    public Path backup(Path destination) {
        rejectActive(destination);
        Path stage = null;
        try {
            stage = AtomicFiles.stage(destination);
            try (var connection = connections.open()) {
                check(connection.unwrap(SQLiteConnection.class).getDatabase().backup("main", stage.toString(), null));
            }
            validate(stage);
            AtomicFiles.publish(stage, destination);
            return destination;
        } catch (Exception failure) {
            throw new PersistenceException("Backup was not published. Choose a writable location with enough space.", failure);
        } finally { AtomicFiles.discard(stage); }
    }

    /** The caller must discard old view state after success. Returns a safety backup of the prior live database. */
    public Path restore(Path backup) {
        rejectActive(backup);
        Path staged = null;
        try {
            if (!Files.isRegularFile(backup) || Files.size(backup) == 0) throw new IllegalArgumentException("Select a nonempty SQLite backup file.");
            staged = AtomicFiles.stage(active);
            // Copy with SQLite even when the source has an active WAL; never copy its raw main file.
            try (var source = readOnly(backup)) {
                validateConnection(source);
                check(source.unwrap(SQLiteConnection.class).getDatabase().backup("main", staged.toString(), null));
            }
            var candidate = new ConnectionFactory(staged);
            new MigrationRunner(candidate).migrate();
            validate(staged);
            validateCurrentSchema(staged);
            var ready = staged;
            return connections.exclusive(() -> {
                var safety = active.resolveSibling("researchflow-before-recovery-" + Instant.now().toEpochMilli() + ".db");
                backup(safety);
                try (var destination = connections.open()) {
                    // SQLite commits the destination only after a successful backup/restore sequence.
                    check(destination.unwrap(SQLiteConnection.class).getDatabase().restore("main", ready.toString(), null));
                }
                return safety;
            });
        } catch (Exception failure) {
            throw new PersistenceException("Recovery was not completed; the current database was retained. "
                    + "Use a valid ResearchFlow backup from a supported version and close other database tools.", failure);
        } finally {
            AtomicFiles.discard(staged);
            if (staged != null) {
                AtomicFiles.discard(Path.of(staged + "-wal")); AtomicFiles.discard(Path.of(staged + "-shm"));
            }
        }
    }

    private void rejectActive(Path file) {
        try {
            var normalized = file.toAbsolutePath().normalize();
            if (normalized.equals(active) || normalized.toString().equals(active + "-wal") || normalized.toString().equals(active + "-shm")
                    || (Files.exists(normalized) && Files.exists(active) && Files.isSameFile(normalized, active))) {
                throw new IllegalArgumentException("Choose a backup file separate from the active database and its WAL files.");
            }
        } catch (java.io.IOException failure) { throw new IllegalArgumentException("Could not resolve the selected backup path.", failure); }
    }

    private static Connection readOnly(Path path) throws java.sql.SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + path.toAbsolutePath().toUri() + "?mode=ro");
    }

    private static void validate(Path file) throws Exception {
        try (var connection = readOnly(file)) { validateConnection(connection); }
    }

    private static void validateCurrentSchema(Path file) throws Exception {
        Path reference = AtomicFiles.stage(file);
        try {
            var expected = new ConnectionFactory(reference);
            new MigrationRunner(expected).migrate();
            try (var schema = expected.open(); var candidate = readOnly(file);
                 var tables = schema.createStatement();
                 var rows = tables.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
                while (rows.next()) {
                    var table = rows.getString(1);
                    try (var columns = schema.createStatement(); var fields = columns.executeQuery("PRAGMA table_info('" + table + "')")) {
                        while (fields.next()) {
                            // Names come only from the application's own migrations.
                            try (var check = candidate.createStatement(); var ignored = check.executeQuery("SELECT " + fields.getString("name") + " FROM " + table + " LIMIT 0")) { }
                        }
                    }
                }
            }
        } finally { AtomicFiles.discard(reference); AtomicFiles.discard(Path.of(reference + "-wal")); AtomicFiles.discard(Path.of(reference + "-shm")); }
    }

    private static void validateConnection(Connection connection) throws Exception {
        try (var statement = connection.createStatement()) {
            try (var rows = statement.executeQuery("PRAGMA integrity_check")) {
                if (!rows.next() || !"ok".equals(rows.getString(1)) || rows.next()) throw new IllegalArgumentException("SQLite integrity check failed.");
            }
            try (var rows = statement.executeQuery("PRAGMA foreign_key_check")) {
                if (rows.next()) throw new IllegalArgumentException("The backup has broken database references.");
            }
            var versions = new java.util.ArrayList<String>();
            try (var rows = statement.executeQuery("SELECT version FROM schema_migrations ORDER BY version")) {
                while (rows.next()) versions.add(rows.getString(1));
            }
            var supported = MigrationRunner.supportedVersions();
            if (versions.isEmpty() || versions.size() > supported.size() || !versions.equals(supported.subList(0, versions.size())))
                throw new IllegalArgumentException("Unsupported or incomplete migration history.");
            for (var table : List.of("studies", "forms", "questions", "responses", "answers", "dataset_versions", "analyses", "findings")) {
                try (var ignored = statement.executeQuery("SELECT * FROM " + table + " LIMIT 0")) { }
            }
        }
    }

    private static void check(int result) throws java.sql.SQLException {
        if (result != 0) throw new java.sql.SQLException("SQLite backup/restore failed with code " + result);
    }
}
