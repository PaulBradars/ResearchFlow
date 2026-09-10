package researchflow.persistence;

import researchflow.domain.QualityIssue;
import researchflow.domain.QualityIssueStatus;
import researchflow.domain.QualityIssueType;
import researchflow.domain.QualitySeverity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcQualityRepository implements QualityRepository {
    private final ConnectionFactory connections;
    private final TransactionManager transactions;

    public JdbcQualityRepository(ConnectionFactory connections, TransactionManager transactions) {
        this.connections = connections;
        this.transactions = transactions;
    }

    @Override
    public void reconcile(UUID studyId, List<QualityIssue> detected) {
        transactions.inTransaction(connection -> {
            JdbcStudyRepository.requireWritable(connection, studyId);
            var detectedKeys = detected.stream().map(QualityIssue::fingerprint)
                    .collect(java.util.stream.Collectors.toSet());
            var stale = new ArrayList<UUID>();
            try (var lookup = connection.prepareStatement(
                    "SELECT * FROM quality_issues WHERE study_id=? AND status IN ('OPEN','DEFERRED')")) {
                lookup.setString(1, studyId.toString());
                try (var rows = lookup.executeQuery()) {
                    while (rows.next()) {
                        var issue = mapIssue(rows);
                        if (!detectedKeys.contains(issue.fingerprint())) stale.add(issue.id());
                    }
                }
            }
            for (var id : stale) updateStatus(connection, id, "RESOLVED",
                    "No longer detected in the current dataset.", "QUALITY_ISSUE_RESOLVED");
            var active = new HashSet<String>();
            try (var statement = connection.prepareStatement(
                    "SELECT issue_type, response_id, question_id FROM quality_issues WHERE study_id=? AND status<>'RESOLVED'")) {
                statement.setString(1, studyId.toString());
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) active.add(rows.getString(1) + '|' + rows.getString(2) + '|' + rows.getString(3));
                }
            }
            var newIssues = 0;
            try (var insert = connection.prepareStatement("""
                    INSERT INTO quality_issues(id, study_id, dataset_version_id, issue_type, severity, status,
                        response_id, question_id, explanation, resolution_note, created_at, resolved_at)
                    VALUES (?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, NULL, ?, NULL)
                    """)) {
                var pending = 0;
                for (var issue : detected) {
                    if (!active.add(issue.fingerprint())) continue;
                    insert.setString(1, issue.id().toString());
                    insert.setString(2, studyId.toString());
                    setNullable(insert, 3, issue.datasetVersionId());
                    insert.setString(4, issue.type().name());
                    insert.setString(5, issue.severity().name());
                    setNullable(insert, 6, issue.responseId());
                    setNullable(insert, 7, issue.questionId());
                    insert.setString(8, issue.explanation());
                    insert.setString(9, issue.createdAt().toString());
                    insert.addBatch();
                    newIssues++;
                    if (++pending >= 500) {
                        insert.executeBatch();
                        pending = 0;
                    }
                }
                if (pending > 0) insert.executeBatch();
            }
            if (newIssues > 0) insertAudit(connection, studyId, "QUALITY_SCAN_COMPLETED", "STUDY", studyId,
                    "{\"newIssues\":" + newIssues + ",\"detected\":" + detected.size() + "}");
            return null;
        });
    }

    @Override
    public List<QualityIssue> findByStudy(UUID studyId, QualityIssueStatus status) {
        var sql = "SELECT * FROM quality_issues WHERE study_id=?" + (status == null ? "" : " AND status=?")
                + " ORDER BY created_at DESC";
        try (var connection = connections.open(); var statement = connection.prepareStatement(sql)) {
            statement.setString(1, studyId.toString());
            if (status != null) statement.setString(2, status.name());
            try (var rows = statement.executeQuery()) {
                var issues = new ArrayList<QualityIssue>();
                while (rows.next()) issues.add(mapIssue(rows));
                return List.copyOf(issues);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load quality issues.", exception);
        }
    }

    @Override
    public Optional<QualityIssue> findById(UUID issueId) {
        try (var connection = connections.open();
             var statement = connection.prepareStatement("SELECT * FROM quality_issues WHERE id=?")) {
            statement.setString(1, issueId.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapIssue(rows)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load the quality issue.", exception);
        }
    }

    @Override
    public void correctAndResolve(UUID issueId, researchflow.domain.CorrectionTarget target,
                                  researchflow.domain.Answer replacement, String reason) {
        transactions.inTransaction(connection -> {
            requireTarget(connection, issueId, target.studyId(), target.responseId(), target.questionId());
            JdbcDatasetRepository.correct(connection, target, replacement, reason);
            updateStatus(connection, issueId, "RESOLVED", reason, "QUALITY_ISSUE_RESOLVED");
            return null;
        });
    }

    private static void requireTarget(Connection connection, UUID issueId, UUID studyId, UUID responseId,
                                      UUID questionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT study_id, response_id, question_id FROM quality_issues WHERE id=?")) {
            statement.setString(1, issueId.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next() || !studyId.toString().equals(rows.getString("study_id"))
                        || responseId == null || !responseId.toString().equals(rows.getString("response_id"))
                        || (questionId != null && !questionId.toString().equals(rows.getString("question_id")))) {
                    throw new IllegalArgumentException("The review target does not match the quality issue.");
                }
            }
        }
    }

    @Override
    public void markResolved(UUID issueId, String resolutionNote) {
        transactions.inTransaction(connection -> {
            updateStatus(connection, issueId, "RESOLVED", resolutionNote, "QUALITY_ISSUE_RESOLVED");
            return null;
        });
    }

    @Override
    public void markAccepted(UUID issueId, String note) {
        transactions.inTransaction(connection -> {
            updateStatus(connection, issueId, "ACCEPTED", note, "QUALITY_ISSUE_ACCEPTED");
            return null;
        });
    }

    @Override
    public void markDeferred(UUID issueId, String note) {
        transactions.inTransaction(connection -> {
            updateStatus(connection, issueId, "DEFERRED", note, "QUALITY_ISSUE_DEFERRED");
            return null;
        });
    }

    @Override
    public void excludeResponse(UUID studyId, UUID issueId, UUID responseId, String reason) {
        transactions.inTransaction(connection -> {
            JdbcStudyRepository.requireWritable(connection, studyId);
            requireTarget(connection, issueId, studyId, responseId, null);
            try (var statement = connection.prepareStatement("""
                    UPDATE responses SET status='EXCLUDED'
                    WHERE id=? AND in_dataset=1 AND form_id IN (SELECT id FROM forms WHERE study_id=?)
                    """)) {
                statement.setString(1, responseId.toString());
                statement.setString(2, studyId.toString());
                if (statement.executeUpdate() != 1) throw new IllegalArgumentException("The response no longer exists.");
            }
            updateStatus(connection, issueId, "RESOLVED", reason, "QUALITY_ISSUE_RESOLVED");
            insertAudit(connection, studyId, "RESPONSE_EXCLUDED", "RESPONSE", responseId,
                    "{\"reason\":\"" + escape(reason) + "\"}");
            return null;
        });
    }

    private static void updateStatus(Connection connection, UUID issueId, String status, String note,
                                     String auditEventType) throws SQLException {
        var now = Instant.now();
        UUID studyId;
        try (var lookup = connection.prepareStatement("SELECT study_id FROM quality_issues WHERE id=?")) {
            lookup.setString(1, issueId.toString());
            try (var rows = lookup.executeQuery()) {
                if (!rows.next()) throw new IllegalArgumentException("The quality issue no longer exists.");
                studyId = UUID.fromString(rows.getString(1));
                JdbcStudyRepository.requireWritable(connection, studyId);
            }
        }
        try (var statement = connection.prepareStatement("""
                UPDATE quality_issues SET status=?, resolution_note=?, resolved_at=? WHERE id=? AND status IN ('OPEN','DEFERRED')
                """)) {
            statement.setString(1, status);
            statement.setString(2, note == null ? "" : note);
            if (status.equals("DEFERRED")) statement.setNull(3, Types.VARCHAR);
            else statement.setString(3, now.toString());
            statement.setString(4, issueId.toString());
            if (statement.executeUpdate() != 1) throw new IllegalArgumentException("The issue was already reviewed. Refresh the issue list.");
        }
        insertAudit(connection, studyId, auditEventType, "QUALITY_ISSUE", issueId,
                "{\"note\":\"" + escape(note == null ? "" : note) + "\"}");
    }

    private static QualityIssue mapIssue(ResultSet row) throws SQLException {
        return new QualityIssue(UUID.fromString(row.getString("id")), UUID.fromString(row.getString("study_id")),
                parseUuid(row.getString("dataset_version_id")), QualityIssueType.valueOf(row.getString("issue_type")),
                QualitySeverity.valueOf(row.getString("severity")), QualityIssueStatus.valueOf(row.getString("status")),
                parseUuid(row.getString("response_id")), parseUuid(row.getString("question_id")),
                row.getString("explanation"), row.getString("resolution_note"),
                Instant.parse(row.getString("created_at")),
                row.getString("resolved_at") == null ? null : Instant.parse(row.getString("resolved_at")));
    }

    private static void insertAudit(Connection connection, UUID studyId, String eventType, String entityType,
                                    UUID entityId, String detailsJson) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO audit_logs(id, study_id, event_type, entity_type, entity_id, actor, occurred_at, details_json)
                VALUES (?, ?, ?, ?, ?, 'local-researcher', ?, ?)
                """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, studyId.toString());
            statement.setString(3, eventType);
            statement.setString(4, entityType);
            statement.setString(5, entityId.toString());
            statement.setString(6, Instant.now().toString());
            statement.setString(7, detailsJson);
            statement.executeUpdate();
        }
    }

    private static void setNullable(PreparedStatement statement, int index, UUID value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value.toString());
    }

    private static UUID parseUuid(String value) {
        if (value == null) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
