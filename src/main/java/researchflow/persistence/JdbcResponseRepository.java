package researchflow.persistence;

import researchflow.domain.Answer;
import researchflow.domain.Form;
import researchflow.domain.Response;
import researchflow.domain.ResponseSummary;

import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JdbcResponseRepository implements ResponseRepository {
    private static final String CHOICE_SEPARATOR = "\u001f";
    private final ConnectionFactory connections;
    private final TransactionManager transactions;

    public JdbcResponseRepository(ConnectionFactory connections, TransactionManager transactions) {
        this.connections = connections;
        this.transactions = transactions;
    }

    @Override
    public void submit(Form form, Response response) {
        transactions.inTransaction(connection -> {
            try (var insert = connection.prepareStatement("""
                    INSERT INTO responses(id, form_id, form_version, status, started_at, submitted_at, duration_seconds)
                    VALUES (?, ?, ?, 'COMPLETE', ?, ?, ?)
                    """)) {
                insert.setString(1, response.id().toString());
                insert.setString(2, response.formId().toString());
                insert.setInt(3, response.formVersion());
                setNullable(insert, 4, response.startedAt());
                insert.setString(5, response.submittedAt().toString());
                if (response.durationSeconds() == null) insert.setNull(6, Types.INTEGER);
                else insert.setLong(6, response.durationSeconds());
                insert.executeUpdate();
            }
            for (var answer : response.answers()) insertAnswer(connection, response.id(), answer);
            try (var audit = connection.prepareStatement("""
                    INSERT INTO audit_logs(id, study_id, event_type, entity_type, entity_id, actor, occurred_at, details_json)
                    VALUES (?, ?, 'RESPONSE_SUBMITTED', 'RESPONSE', ?, 'local-respondent', ?, '{}')
                    """)) {
                audit.setString(1, UUID.randomUUID().toString());
                audit.setString(2, form.studyId().toString());
                audit.setString(3, response.id().toString());
                audit.setString(4, response.submittedAt().toString());
                audit.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<ResponseSummary> findByStudy(UUID studyId) {
        try (var connection = connections.open();
             var statement = connection.prepareStatement("""
                     SELECT r.id, r.form_id, f.title, r.submitted_at, r.duration_seconds, COUNT(a.id) answer_count
                     FROM responses r JOIN forms f ON f.id = r.form_id
                     LEFT JOIN answers a ON a.response_id = r.id
                     WHERE f.study_id = ? GROUP BY r.id
                     ORDER BY r.submitted_at DESC
                     """)) {
            statement.setString(1, studyId.toString());
            try (var rows = statement.executeQuery()) {
                var values = new ArrayList<ResponseSummary>();
                while (rows.next()) values.add(new ResponseSummary(UUID.fromString(rows.getString("id")),
                        UUID.fromString(rows.getString("form_id")), rows.getString("title"),
                        Instant.parse(rows.getString("submitted_at")),
                        rows.getObject("duration_seconds") == null ? null : rows.getLong("duration_seconds"),
                        rows.getInt("answer_count")));
                return List.copyOf(values);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load responses.", exception);
        }
    }

    @Override
    public List<Response> findFullByStudy(UUID studyId) {
        try (var connection = connections.open();
             var statement = connection.prepareStatement("""
                     SELECT r.id, r.form_id, r.form_version, r.started_at, r.submitted_at, r.duration_seconds
                     FROM responses r JOIN forms f ON f.id = r.form_id
                     WHERE f.study_id = ? AND r.status = 'COMPLETE'
                     ORDER BY r.submitted_at
                     """)) {
            statement.setString(1, studyId.toString());
            var results = new ArrayList<Response>();
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    var id = UUID.fromString(rows.getString("id"));
                    results.add(new Response(id, UUID.fromString(rows.getString("form_id")), rows.getInt("form_version"),
                            parseNullableInstant(rows.getString("started_at")), Instant.parse(rows.getString("submitted_at")),
                            rows.getObject("duration_seconds") == null ? null : rows.getLong("duration_seconds"),
                            loadTypedAnswers(connection, id)));
                }
            }
            return List.copyOf(results);
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load responses for quality review.", exception);
        }
    }

    private static List<Answer> loadTypedAnswers(java.sql.Connection connection, UUID responseId) throws SQLException {
        try (var statement = connection.prepareStatement("""
                SELECT a.id, a.value_text, a.value_number, a.value_boolean, a.value_date, a.created_at,
                       a.question_id, q.question_type
                FROM answers a JOIN questions q ON q.id = a.question_id WHERE a.response_id = ?
                """)) {
            statement.setString(1, responseId.toString());
            try (var rows = statement.executeQuery()) {
                var answers = new ArrayList<Answer>();
                while (rows.next()) answers.add(mapTypedAnswer(rows));
                return answers;
            }
        }
    }

    private static Answer mapTypedAnswer(java.sql.ResultSet row) throws SQLException {
        var id = UUID.fromString(row.getString("id"));
        var questionId = UUID.fromString(row.getString("question_id"));
        var createdAt = Instant.parse(row.getString("created_at"));
        var type = row.getString("question_type");
        var text = row.getString("value_text");
        if (text != null) {
            if (type.equals("MULTIPLE_CHOICE") || type.equals("SINGLE_CHOICE") || type.equals("LIKERT") || type.equals("RATING"))
                return new Answer.Choice(id, questionId, java.util.Arrays.asList(text.split(CHOICE_SEPARATOR, -1)), createdAt);
            return new Answer.Text(id, questionId, text, createdAt);
        }
        var number = row.getObject("value_number");
        if (number != null) return new Answer.Number(id, questionId, row.getDouble("value_number"), createdAt);
        var bool = row.getObject("value_boolean");
        if (bool != null) return new Answer.BooleanValue(id, questionId, row.getInt("value_boolean") == 1, createdAt);
        var date = row.getString("value_date");
        if (date != null) return new Answer.DateValue(id, questionId, java.time.LocalDate.parse(date), createdAt);
        throw new SQLException("Answer " + id + " has no recorded value.");
    }

    private static Instant parseNullableInstant(String value) {
        return value == null ? null : Instant.parse(value);
    }

    private static void insertAnswer(java.sql.Connection connection, UUID responseId, Answer answer) throws SQLException {
        try (var insert = connection.prepareStatement("""
                INSERT INTO answers(id, response_id, question_id, value_text, value_number, value_boolean,
                                    value_date, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            insert.setString(1, answer.id().toString());
            insert.setString(2, responseId.toString());
            insert.setString(3, answer.questionId().toString());
            for (int index = 4; index <= 7; index++) insert.setNull(index, Types.NULL);
            if (answer instanceof Answer.Text value) insert.setString(4, value.value());
            else if (answer instanceof Answer.Number value) insert.setDouble(5, value.value());
            else if (answer instanceof Answer.BooleanValue value) insert.setInt(6, value.value() ? 1 : 0);
            else if (answer instanceof Answer.DateValue value) insert.setString(7, value.value().toString());
            else if (answer instanceof Answer.Choice value) insert.setString(4, String.join(CHOICE_SEPARATOR, value.values()));
            insert.setString(8, answer.createdAt().toString());
            insert.setString(9, answer.createdAt().toString());
            insert.executeUpdate();
        }
    }

    private static void setNullable(java.sql.PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) statement.setNull(index, Types.VARCHAR);
        else statement.setString(index, value.toString());
    }
}

