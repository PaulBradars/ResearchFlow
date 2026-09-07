package researchflow.persistence;

import researchflow.domain.DatasetVersion;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcVersionRepository implements VersionRepository {
    private final ConnectionFactory connections;
    private final TransactionManager transactions;

    public JdbcVersionRepository(ConnectionFactory connections, TransactionManager transactions) {
        this.connections = connections;
        this.transactions = transactions;
    }

    @Override
    public DatasetVersion createSnapshot(UUID studyId, String reason, String changeSummary) {
        return transactions.inTransaction(connection -> {
            var normalizedSummary = changeSummary == null ? "" : changeSummary;
            var parent = findActiveId(connection, studyId);
            var versionNumber = nextVersionNumber(connection, studyId);
            var id = UUID.randomUUID();
            var now = Instant.now();
            deactivateAll(connection, studyId);
            insertVersion(connection, id, studyId, parent, versionNumber, reason, normalizedSummary, now);
            snapshotCurrentAnswers(connection, studyId, id);
            insertAudit(connection, studyId, "DATASET_VERSION_CREATED", id,
                    "{\"versionNumber\":" + versionNumber + "}");
            return new DatasetVersion(id, studyId, parent, versionNumber, reason, normalizedSummary, now, true);
        });
    }

    @Override
    public List<DatasetVersion> findByStudy(UUID studyId) {
        try (var connection = connections.open(); var statement = connection.prepareStatement(
                "SELECT * FROM dataset_versions WHERE study_id=? ORDER BY version_number DESC")) {
            statement.setString(1, studyId.toString());
            try (var rows = statement.executeQuery()) {
                var versions = new ArrayList<DatasetVersion>();
                while (rows.next()) versions.add(mapVersion(rows));
                return List.copyOf(versions);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load dataset versions.", exception);
        }
    }

    @Override
    public Optional<DatasetVersion> findActive(UUID studyId) {
        try (var connection = connections.open(); var statement = connection.prepareStatement(
                "SELECT * FROM dataset_versions WHERE study_id=? AND is_active=1")) {
            statement.setString(1, studyId.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapVersion(rows)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load the active dataset version.", exception);
        }
    }

    @Override
    public DatasetVersion restore(UUID studyId, UUID targetVersionId, String reason) {
        return transactions.inTransaction(connection -> {
            var targetNumber = versionNumberOf(connection, studyId, targetVersionId);
            applySnapshot(connection, targetVersionId);

            var parent = findActiveId(connection, studyId);
            var versionNumber = nextVersionNumber(connection, studyId);
            var id = UUID.randomUUID();
            var now = Instant.now();
            var fullReason = "Restored to version " + targetNumber + ": " + reason;
            var summary = "Restored from version " + targetNumber + ".";
            deactivateAll(connection, studyId);
            insertVersion(connection, id, studyId, parent, versionNumber, fullReason, summary, now);
            snapshotCurrentAnswers(connection, studyId, id);
            insertAudit(connection, studyId, "DATASET_VERSION_RESTORED", id,
                    "{\"versionNumber\":" + versionNumber + ",\"restoredFromVersion\":" + targetNumber + "}");
            return new DatasetVersion(id, studyId, parent, versionNumber, fullReason, summary, now, true);
        });
    }

    /** Flushing in bounded chunks keeps memory flat on a very large Study without losing batching's speed. */
    private static final int BATCH_SIZE = 500;

    private static void applySnapshot(Connection connection, UUID targetVersionId) throws SQLException {
        var excluded = new LinkedHashSet<String>();
        var included = new LinkedHashSet<String>();
        try (var select = connection.prepareStatement(
                "SELECT answer_id, response_id, question_id, value_json, excluded FROM version_answer_snapshots WHERE dataset_version_id=?");
             var upsert = connection.prepareStatement("""
                INSERT INTO answers(id, response_id, question_id, value_text, value_number, value_boolean,
                    value_date, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(response_id, question_id) DO UPDATE SET
                    value_text=excluded.value_text, value_number=excluded.value_number,
                    value_boolean=excluded.value_boolean, value_date=excluded.value_date, updated_at=excluded.updated_at
                """)) {
            select.setString(1, targetVersionId.toString());
            var now = Instant.now().toString();
            var pending = 0;
            try (var rows = select.executeQuery()) {
                while (rows.next()) {
                    addRestoreBatch(upsert, rows.getString("answer_id"), rows.getString("response_id"),
                            rows.getString("question_id"), rows.getString("value_json"), now);
                    (rows.getInt("excluded") == 1 ? excluded : included).add(rows.getString("response_id"));
                    if (++pending >= BATCH_SIZE) {
                        upsert.executeBatch();
                        pending = 0;
                    }
                }
            }
            if (pending > 0) upsert.executeBatch();
        }
        setResponseStatuses(connection, excluded, "EXCLUDED");
        setResponseStatuses(connection, included, "COMPLETE");
    }

    private static void addRestoreBatch(PreparedStatement upsert, String answerId, String responseId, String questionId,
                                        String valueJson, String now) throws SQLException {
        var value = SnapshotJson.parse(valueJson);
        upsert.setString(1, answerId);
        upsert.setString(2, responseId);
        upsert.setString(3, questionId);
        if (value.text() != null) upsert.setString(4, value.text()); else upsert.setNull(4, Types.VARCHAR);
        if (value.number() != null) upsert.setDouble(5, value.number()); else upsert.setNull(5, Types.REAL);
        if (value.bool() != null) upsert.setInt(6, value.bool()); else upsert.setNull(6, Types.INTEGER);
        if (value.date() != null) upsert.setString(7, value.date()); else upsert.setNull(7, Types.VARCHAR);
        upsert.setString(8, now);
        upsert.setString(9, now);
        upsert.addBatch();
    }

    private static void setResponseStatuses(Connection connection, LinkedHashSet<String> responseIds, String status)
            throws SQLException {
        if (responseIds.isEmpty()) return;
        try (var statement = connection.prepareStatement("UPDATE responses SET status=? WHERE id=?")) {
            var pending = 0;
            for (var responseId : responseIds) {
                statement.setString(1, status);
                statement.setString(2, responseId);
                statement.addBatch();
                if (++pending >= BATCH_SIZE) {
                    statement.executeBatch();
                    pending = 0;
                }
            }
            if (pending > 0) statement.executeBatch();
        }
    }

    private static void snapshotCurrentAnswers(Connection connection, UUID studyId, UUID versionId) throws SQLException {
        try (var select = connection.prepareStatement("""
                SELECT a.id answer_id, a.response_id, a.question_id, a.value_text, a.value_number,
                       a.value_boolean, a.value_date, r.status
                FROM answers a JOIN responses r ON r.id=a.response_id JOIN forms f ON f.id=r.form_id
                WHERE f.study_id=?
                """);
             var insert = connection.prepareStatement("""
                INSERT INTO version_answer_snapshots(dataset_version_id, answer_id, response_id, question_id, value_json, excluded)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            select.setString(1, studyId.toString());
            var pending = 0;
            try (var rows = select.executeQuery()) {
                while (rows.next()) {
                    insert.setString(1, versionId.toString());
                    insert.setString(2, rows.getString("answer_id"));
                    insert.setString(3, rows.getString("response_id"));
                    insert.setString(4, rows.getString("question_id"));
                    insert.setString(5, SnapshotJson.fromColumns(rows));
                    insert.setInt(6, "EXCLUDED".equals(rows.getString("status")) ? 1 : 0);
                    insert.addBatch();
                    if (++pending >= BATCH_SIZE) {
                        insert.executeBatch();
                        pending = 0;
                    }
                }
            }
            if (pending > 0) insert.executeBatch();
        }
    }

    private static UUID findActiveId(Connection connection, UUID studyId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT id FROM dataset_versions WHERE study_id=? AND is_active=1")) {
            statement.setString(1, studyId.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? UUID.fromString(rows.getString(1)) : null;
            }
        }
    }

    private static int nextVersionNumber(Connection connection, UUID studyId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(version_number), 0) + 1 FROM dataset_versions WHERE study_id=?")) {
            statement.setString(1, studyId.toString());
            try (var rows = statement.executeQuery()) { rows.next(); return rows.getInt(1); }
        }
    }

    private static int versionNumberOf(Connection connection, UUID studyId, UUID versionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT version_number FROM dataset_versions WHERE id=? AND study_id=?")) {
            statement.setString(1, versionId.toString());
            statement.setString(2, studyId.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("The dataset version no longer exists.");
                return rows.getInt(1);
            }
        }
    }

    private static void deactivateAll(Connection connection, UUID studyId) throws SQLException {
        try (var statement = connection.prepareStatement("UPDATE dataset_versions SET is_active=0 WHERE study_id=?")) {
            statement.setString(1, studyId.toString());
            statement.executeUpdate();
        }
    }

    private static void insertVersion(Connection connection, UUID id, UUID studyId, UUID parent, int versionNumber,
                                      String reason, String changeSummary, Instant now) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO dataset_versions(id, study_id, parent_version_id, version_number, reason,
                    change_summary, created_at, is_active)
                VALUES (?, ?, ?, ?, ?, ?, ?, 1)
                """)) {
            statement.setString(1, id.toString());
            statement.setString(2, studyId.toString());
            if (parent == null) statement.setNull(3, Types.VARCHAR); else statement.setString(3, parent.toString());
            statement.setInt(4, versionNumber);
            statement.setString(5, reason);
            statement.setString(6, changeSummary);
            statement.setString(7, now.toString());
            statement.executeUpdate();
        }
    }

    private static void insertAudit(Connection connection, UUID studyId, String eventType, UUID entityId,
                                    String detailsJson) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO audit_logs(id, study_id, event_type, entity_type, entity_id, actor, occurred_at, details_json)
                VALUES (?, ?, ?, 'VERSION', ?, 'local-researcher', ?, ?)
                """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, studyId.toString());
            statement.setString(3, eventType);
            statement.setString(4, entityId.toString());
            statement.setString(5, Instant.now().toString());
            statement.setString(6, detailsJson);
            statement.executeUpdate();
        }
    }

    private static DatasetVersion mapVersion(ResultSet row) throws SQLException {
        var parent = row.getString("parent_version_id");
        return new DatasetVersion(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("study_id")),
                parent == null ? null : UUID.fromString(parent), row.getInt("version_number"),
                row.getString("reason"), row.getString("change_summary"),
                Instant.parse(row.getString("created_at")), row.getInt("is_active") == 1);
    }

}
