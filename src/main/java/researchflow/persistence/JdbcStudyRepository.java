package researchflow.persistence;

import researchflow.domain.Study;
import researchflow.domain.StudyMetrics;
import researchflow.domain.StudyStatus;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcStudyRepository implements StudyRepository {
    private final ConnectionFactory connections;
    private final TransactionManager transactions;

    public JdbcStudyRepository(ConnectionFactory connections, TransactionManager transactions) {
        this.connections = connections;
        this.transactions = transactions;
    }

    @Override
    public void save(Study study, String auditEventType) {
        transactions.inTransaction(connection -> {
            upsertStudy(connection, study);
            replaceResearchQuestions(connection, study);
            insertAuditEvent(connection, study.id(), auditEventType);
            return null;
        });
    }

    @Override
    public Optional<Study> findById(UUID id) {
        try (var connection = connections.open();
             var statement = connection.prepareStatement("SELECT * FROM studies WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (var results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapStudy(connection, results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load the study.", exception);
        }
    }

    @Override
    public List<Study> findAll(boolean includeArchived) {
        var sql = "SELECT * FROM studies" + (includeArchived ? "" : " WHERE status = 'ACTIVE'")
                + " ORDER BY updated_at DESC, title COLLATE NOCASE";
        try (var connection = connections.open();
             var statement = connection.prepareStatement(sql);
             var results = statement.executeQuery()) {
            var studies = new ArrayList<Study>();
            while (results.next()) {
                studies.add(mapStudy(connection, results));
            }
            return List.copyOf(studies);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load studies.", exception);
        }
    }

    @Override
    public StudyMetrics metrics(UUID studyId) {
        var sql = """
                SELECT
                    (SELECT COUNT(*) FROM forms WHERE study_id = ?) AS forms,
                    (SELECT COUNT(*) FROM responses r JOIN forms f ON f.id = r.form_id WHERE f.study_id = ?) AS responses,
                    (SELECT COUNT(*) FROM quality_issues WHERE study_id = ? AND status IN ('OPEN', 'DEFERRED')) AS issues,
                    (SELECT COUNT(*) FROM dataset_versions WHERE study_id = ?) AS versions,
                    (SELECT COUNT(*) FROM analyses WHERE study_id = ?) AS analyses,
                    (SELECT COUNT(*) FROM findings WHERE study_id = ? AND status = 'APPROVED') AS findings
                """;
        try (var connection = connections.open(); var statement = connection.prepareStatement(sql)) {
            for (int index = 1; index <= 6; index++) {
                statement.setString(index, studyId.toString());
            }
            try (var results = statement.executeQuery()) {
                return results.next()
                        ? new StudyMetrics(results.getLong("forms"), results.getLong("responses"),
                        results.getLong("issues"), results.getLong("versions"),
                        results.getLong("analyses"), results.getLong("findings"))
                        : StudyMetrics.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load study metrics.", exception);
        }
    }

    private static void upsertStudy(Connection connection, Study study) throws SQLException {
        var sql = """
                INSERT INTO studies(id, title, description, objectives, researcher, start_date, end_date,
                                    status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    title = excluded.title,
                    description = excluded.description,
                    objectives = excluded.objectives,
                    researcher = excluded.researcher,
                    start_date = excluded.start_date,
                    end_date = excluded.end_date,
                    status = excluded.status,
                    updated_at = excluded.updated_at
                """;
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, study.id().toString());
            statement.setString(2, study.title());
            statement.setString(3, study.description());
            statement.setString(4, study.objectives());
            statement.setString(5, study.researcher());
            setNullable(statement, 6, study.startDate());
            setNullable(statement, 7, study.endDate());
            statement.setString(8, study.status().name());
            statement.setString(9, study.createdAt().toString());
            statement.setString(10, study.updatedAt().toString());
            statement.executeUpdate();
        }
    }

    private static void replaceResearchQuestions(Connection connection, Study study) throws SQLException {
        try (var delete = connection.prepareStatement("DELETE FROM research_questions WHERE study_id = ?")) {
            delete.setString(1, study.id().toString());
            delete.executeUpdate();
        }
        var sql = "INSERT INTO research_questions(id, study_id, question_text, position, created_at) VALUES (?, ?, ?, ?, ?)";
        try (var insert = connection.prepareStatement(sql)) {
            for (int index = 0; index < study.researchQuestions().size(); index++) {
                insert.setString(1, UUID.randomUUID().toString());
                insert.setString(2, study.id().toString());
                insert.setString(3, study.researchQuestions().get(index));
                insert.setInt(4, index);
                insert.setString(5, study.updatedAt().toString());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private static void insertAuditEvent(Connection connection, UUID studyId, String eventType) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO audit_logs(id, study_id, event_type, entity_type, entity_id, actor, occurred_at, details_json)
                VALUES (?, ?, ?, 'STUDY', ?, 'local-researcher', ?, '{}')
                """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, studyId.toString());
            statement.setString(3, eventType);
            statement.setString(4, studyId.toString());
            statement.setString(5, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private static Study mapStudy(Connection connection, ResultSet results) throws SQLException {
        var id = UUID.fromString(results.getString("id"));
        return new Study(id, results.getString("title"), results.getString("description"),
                results.getString("objectives"), results.getString("researcher"),
                parseDate(results.getString("start_date")), parseDate(results.getString("end_date")),
                StudyStatus.valueOf(results.getString("status")), loadQuestions(connection, id),
                Instant.parse(results.getString("created_at")), Instant.parse(results.getString("updated_at")));
    }

    private static List<String> loadQuestions(Connection connection, UUID studyId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT question_text FROM research_questions WHERE study_id = ? ORDER BY position")) {
            statement.setString(1, studyId.toString());
            try (var results = statement.executeQuery()) {
                var questions = new ArrayList<String>();
                while (results.next()) {
                    questions.add(results.getString(1));
                }
                return List.copyOf(questions);
            }
        }
    }

    private static void setNullable(java.sql.PreparedStatement statement, int index, LocalDate value) throws SQLException {
        if (value == null) {
            statement.setNull(index, java.sql.Types.VARCHAR);
        } else {
            statement.setString(index, value.toString());
        }
    }

    private static LocalDate parseDate(String value) {
        return value == null ? null : LocalDate.parse(value);
    }
}
