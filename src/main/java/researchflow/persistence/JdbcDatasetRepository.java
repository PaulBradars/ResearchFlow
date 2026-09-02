package researchflow.persistence;

import researchflow.domain.Answer;
import researchflow.domain.AuditEvent;
import researchflow.domain.CorrectionTarget;
import researchflow.domain.DatasetCell;
import researchflow.domain.DatasetFilterOperator;
import researchflow.domain.DatasetPage;
import researchflow.domain.DatasetQuery;
import researchflow.domain.DatasetRow;
import researchflow.domain.DatasetSort;
import researchflow.domain.DatasetVariable;
import researchflow.domain.QuestionType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcDatasetRepository implements DatasetRepository, AuditRepository {
    private static final String CHOICE_SEPARATOR = "\u001f";
    private final ConnectionFactory connections;
    private final TransactionManager transactions;

    public JdbcDatasetRepository(ConnectionFactory connections, TransactionManager transactions) {
        this.connections = connections;
        this.transactions = transactions;
    }

    @Override
    public DatasetPage query(UUID studyId, DatasetQuery query) {
        try (var connection = connections.open()) {
            var variables = loadVariables(connection, studyId, query.formId());
            var countParams = new ArrayList<Object>();
            var where = buildWhere(studyId, query, countParams);
            long count;
            try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM responses r JOIN forms f ON f.id=r.form_id " + where)) {
                bind(statement, countParams);
                try (var rows = statement.executeQuery()) { rows.next(); count = rows.getLong(1); }
            }

            var params = new ArrayList<Object>();
            where = buildWhere(studyId, query, params);
            var order = orderBy(query, params);
            params.add(query.limit()); params.add(query.offset());
            var sql = "SELECT r.id, r.form_id, f.title, r.status, r.submitted_at, r.duration_seconds "
                    + "FROM responses r JOIN forms f ON f.id=r.form_id " + where + order + " LIMIT ? OFFSET ?";
            var result = new ArrayList<DatasetRow>();
            try (var statement = connection.prepareStatement(sql)) {
                bind(statement, params);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) result.add(mapRow(connection, rows));
                }
            }
            return new DatasetPage(variables, result, count, query.offset(), query.limit());
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load the dataset.", exception);
        }
    }

    @Override
    public Optional<DatasetRow> findResponse(UUID studyId, UUID responseId) {
        try (var connection = connections.open();
             var statement = connection.prepareStatement("""
                     SELECT r.id, r.form_id, f.title, r.status, r.submitted_at, r.duration_seconds
                     FROM responses r JOIN forms f ON f.id=r.form_id
                     WHERE f.study_id=? AND r.id=?
                     """)) {
            statement.setString(1, studyId.toString()); statement.setString(2, responseId.toString());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? Optional.of(mapRow(connection, rows)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load response details.", exception);
        }
    }

    @Override
    public Optional<CorrectionTarget> findCorrectionTarget(UUID studyId, UUID responseId, UUID questionId) {
        try (var connection = connections.open();
             var statement = connection.prepareStatement("""
                     SELECT f.study_id, f.id form_id, r.id response_id, a.id answer_id, q.id question_id
                     FROM responses r JOIN forms f ON f.id=r.form_id
                     JOIN questions q ON q.form_id=f.id
                     LEFT JOIN answers a ON a.response_id=r.id AND a.question_id=q.id
                     WHERE f.study_id=? AND r.id=? AND q.id=?
                     """)) {
            statement.setString(1, studyId.toString()); statement.setString(2, responseId.toString());
            statement.setString(3, questionId.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) return Optional.empty();
                var answer = rows.getString("answer_id");
                return Optional.of(new CorrectionTarget(studyId, UUID.fromString(rows.getString("form_id")),
                        responseId, answer == null ? null : UUID.fromString(answer), questionId));
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load the correction target.", exception);
        }
    }

    @Override
    public void correct(CorrectionTarget target, Answer replacement, String reason) {
        transactions.inTransaction(connection -> {
            var answerId = target.answerId() == null ? replacement.id() : target.answerId();
            var oldJson = target.answerId() == null ? "null" : loadValueJson(connection, target.answerId());
            if (target.answerId() == null) insertAnswer(connection, target.responseId(), replacement);
            else updateAnswer(connection, target.answerId(), replacement);
            var newJson = answerJson(replacement);
            var now = Instant.now();
            try (var correction = connection.prepareStatement("""
                    INSERT INTO answer_corrections(id, study_id, response_id, question_id, answer_id,
                        old_value_json, new_value_json, reason, actor, corrected_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'local-researcher', ?)
                    """)) {
                correction.setString(1, UUID.randomUUID().toString());
                correction.setString(2, target.studyId().toString());
                correction.setString(3, target.responseId().toString());
                correction.setString(4, target.questionId().toString());
                correction.setString(5, answerId.toString());
                correction.setString(6, oldJson); correction.setString(7, newJson);
                correction.setString(8, reason); correction.setString(9, now.toString());
                correction.executeUpdate();
            }
            try (var audit = connection.prepareStatement("""
                    INSERT INTO audit_logs(id, study_id, event_type, entity_type, entity_id, actor, occurred_at, details_json)
                    VALUES (?, ?, 'ANSWER_CORRECTED', 'ANSWER', ?, 'local-researcher', ?, ?)
                    """)) {
                audit.setString(1, UUID.randomUUID().toString()); audit.setString(2, target.studyId().toString());
                audit.setString(3, answerId.toString()); audit.setString(4, now.toString());
                audit.setString(5, "{\"responseId\":\"" + target.responseId() + "\",\"questionId\":\""
                        + target.questionId() + "\",\"reason\":\"" + escape(reason) + "\"}");
                audit.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<AuditEvent> findByStudy(UUID studyId, int limit) {
        var bounded = Math.max(1, Math.min(500, limit));
        try (var connection = connections.open();
             var statement = connection.prepareStatement("""
                     SELECT * FROM audit_logs WHERE study_id=? ORDER BY occurred_at DESC LIMIT ?
                     """)) {
            statement.setString(1, studyId.toString()); statement.setInt(2, bounded);
            try (var rows = statement.executeQuery()) {
                var events = new ArrayList<AuditEvent>();
                while (rows.next()) events.add(new AuditEvent(UUID.fromString(rows.getString("id")),
                        rows.getString("event_type"), rows.getString("entity_type"),
                        parseUuid(rows.getString("entity_id")), rows.getString("actor"),
                        Instant.parse(rows.getString("occurred_at")), rows.getString("details_json")));
                return List.copyOf(events);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load the audit timeline.", exception);
        }
    }

    private static String buildWhere(UUID studyId, DatasetQuery query, List<Object> params) {
        var sql = new StringBuilder(" WHERE f.study_id=?"); params.add(studyId);
        if (query.formId() != null) { sql.append(" AND f.id=?"); params.add(query.formId()); }
        if (!query.search().isBlank()) {
            var pattern = "%" + query.search().toLowerCase() + "%";
            sql.append(" AND (lower(f.title) LIKE ? OR lower(r.id) LIKE ? OR EXISTS (SELECT 1 FROM answers sa ")
                    .append("WHERE sa.response_id=r.id AND lower(COALESCE(sa.value_text, CAST(sa.value_number AS TEXT), ")
                    .append("CASE sa.value_boolean WHEN 1 THEN 'yes' WHEN 0 THEN 'no' END, sa.value_date, '')) LIKE ?))");
            params.add(pattern); params.add(pattern); params.add(pattern);
        }
        if (query.filterQuestionId() != null && query.filterOperator() != null) {
            if (query.filterOperator() == DatasetFilterOperator.IS_MISSING) {
                sql.append(" AND r.form_id=(SELECT form_id FROM questions WHERE id=?)")
                        .append(" AND NOT EXISTS (SELECT 1 FROM answers fa WHERE fa.response_id=r.id AND fa.question_id=? ")
                        .append("AND (fa.value_text IS NOT NULL OR fa.value_number IS NOT NULL OR fa.value_boolean IS NOT NULL OR fa.value_date IS NOT NULL))");
                params.add(query.filterQuestionId()); params.add(query.filterQuestionId());
            } else {
                sql.append(" AND EXISTS (SELECT 1 FROM answers fa JOIN questions fq ON fq.id=fa.question_id ")
                        .append("WHERE fa.response_id=r.id AND fa.question_id=? AND ");
                params.add(query.filterQuestionId());
                switch (query.filterOperator()) {
                    case CONTAINS -> {
                        sql.append("lower(COALESCE(fa.value_text, CAST(fa.value_number AS TEXT), CASE fa.value_boolean WHEN 1 THEN 'yes' WHEN 0 THEN 'no' END, fa.value_date, '')) LIKE ?)");
                        params.add("%" + query.filterValue().toLowerCase() + "%");
                    }
                    case EQUALS -> {
                        sql.append("lower(COALESCE(fa.value_text, CAST(fa.value_number AS TEXT), CASE fa.value_boolean WHEN 1 THEN 'yes' WHEN 0 THEN 'no' END, fa.value_date, '')) = ?)");
                        params.add(query.filterValue().toLowerCase());
                    }
                    case GREATER_THAN, LESS_THAN -> {
                        var op = query.filterOperator() == DatasetFilterOperator.GREATER_THAN ? ">" : "<";
                        sql.append("((fq.question_type='NUMBER' AND fa.value_number ").append(op).append(" CAST(? AS REAL)) ")
                                .append("OR (fq.question_type='DATE' AND fa.value_date ").append(op).append(" ?)))");
                        params.add(query.filterValue()); params.add(query.filterValue());
                    }
                    default -> throw new IllegalArgumentException("Unsupported filter operator.");
                }
            }
        }
        return sql.toString();
    }

    private static String orderBy(DatasetQuery query, List<Object> params) {
        return switch (query.sort()) {
            case NEWEST -> " ORDER BY r.submitted_at DESC";
            case OLDEST -> " ORDER BY r.submitted_at ASC";
            case DURATION_ASC -> " ORDER BY r.duration_seconds IS NULL, r.duration_seconds ASC";
            case DURATION_DESC -> " ORDER BY r.duration_seconds IS NULL, r.duration_seconds DESC";
            case VARIABLE_ASC, VARIABLE_DESC -> {
                if (query.sortQuestionId() == null) yield " ORDER BY r.submitted_at DESC";
                params.add(query.sortQuestionId());
                yield " ORDER BY (SELECT COALESCE(va.value_text, CAST(va.value_number AS TEXT), CAST(va.value_boolean AS TEXT), va.value_date) FROM answers va WHERE va.response_id=r.id AND va.question_id=?) "
                        + (query.sort() == DatasetSort.VARIABLE_ASC ? "ASC" : "DESC");
            }
        };
    }

    private static List<DatasetVariable> loadVariables(Connection connection, UUID studyId, UUID formId) throws SQLException {
        var sql = "SELECT q.id, q.form_id, f.title, q.variable_key, q.label, q.question_type, q.required, q.configuration_json "
                + "FROM questions q JOIN forms f ON f.id=q.form_id WHERE f.study_id=?"
                + (formId == null ? "" : " AND f.id=?") + " ORDER BY f.created_at, q.position";
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, studyId.toString());
            if (formId != null) statement.setString(2, formId.toString());
            try (var rows = statement.executeQuery()) {
                var values = new ArrayList<DatasetVariable>();
                while (rows.next()) values.add(new DatasetVariable(UUID.fromString(rows.getString("id")),
                        UUID.fromString(rows.getString("form_id")), rows.getString("title"),
                        rows.getString("variable_key"), rows.getString("label"),
                        QuestionType.valueOf(rows.getString("question_type")), rows.getInt("required") == 1,
                        configNumber(rows.getString("configuration_json"), "min"),
                        configNumber(rows.getString("configuration_json"), "max"),
                        loadOptionValues(connection, UUID.fromString(rows.getString("id")))));
                return List.copyOf(values);
            }
        }
    }

    private static List<String> loadOptionValues(Connection connection, UUID questionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT option_value FROM question_options WHERE question_id=? ORDER BY position")) {
            statement.setString(1, questionId.toString());
            try (var rows = statement.executeQuery()) {
                var values = new ArrayList<String>(); while (rows.next()) values.add(rows.getString(1));
                return List.copyOf(values);
            }
        }
    }

    private static Double configNumber(String json, String key) {
        if (json == null) return null;
        var matcher = java.util.regex.Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").matcher(json);
        return matcher.find() ? Double.valueOf(matcher.group(1)) : null;
    }

    private static DatasetRow mapRow(Connection connection, ResultSet row) throws SQLException {
        var responseId = UUID.fromString(row.getString("id"));
        var cells = new LinkedHashMap<UUID, DatasetCell>();
        try (var statement = connection.prepareStatement("""
                SELECT a.id, a.question_id, a.value_text, a.value_number, a.value_boolean, a.value_date, q.question_type
                FROM answers a JOIN questions q ON q.id=a.question_id WHERE a.response_id=?
                """)) {
            statement.setString(1, responseId.toString());
            try (var answers = statement.executeQuery()) {
                while (answers.next()) {
                    var questionId = UUID.fromString(answers.getString("question_id"));
                    cells.put(questionId, new DatasetCell(UUID.fromString(answers.getString("id")), questionId,
                            displayValue(answers), false));
                }
            }
        }
        return new DatasetRow(responseId, UUID.fromString(row.getString("form_id")), row.getString("title"),
                row.getString("status"), Instant.parse(row.getString("submitted_at")),
                row.getObject("duration_seconds") == null ? null : row.getLong("duration_seconds"), cells);
    }

    private static String displayValue(ResultSet row) throws SQLException {
        var text = row.getString("value_text");
        if (text != null) return row.getString("question_type").equals("MULTIPLE_CHOICE")
                ? String.join(", ", text.split(CHOICE_SEPARATOR, -1)) : text;
        var number = row.getObject("value_number"); if (number != null) return Double.toString(row.getDouble("value_number"));
        var bool = row.getObject("value_boolean"); if (bool != null) return row.getInt("value_boolean") == 1 ? "Yes" : "No";
        var date = row.getString("value_date"); return date == null ? "—" : date;
    }

    private static void insertAnswer(Connection connection, UUID responseId, Answer answer) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO answers(id, response_id, question_id, value_text, value_number, value_boolean, value_date, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, answer.id().toString()); statement.setString(2, responseId.toString());
            statement.setString(3, answer.questionId().toString()); setAnswerValues(statement, 4, answer);
            statement.setString(8, answer.createdAt().toString()); statement.setString(9, answer.createdAt().toString());
            statement.executeUpdate();
        }
    }

    private static void updateAnswer(Connection connection, UUID answerId, Answer answer) throws SQLException {
        try (var statement = connection.prepareStatement("""
                UPDATE answers SET value_text=?, value_number=?, value_boolean=?, value_date=?, updated_at=? WHERE id=?
                """)) {
            setAnswerValues(statement, 1, answer); statement.setString(5, answer.createdAt().toString());
            statement.setString(6, answerId.toString());
            if (statement.executeUpdate() != 1) throw new SQLException("Answer no longer exists.");
        }
    }

    private static void setAnswerValues(PreparedStatement statement, int start, Answer answer) throws SQLException {
        for (int index = start; index < start + 4; index++) statement.setNull(index, Types.NULL);
        if (answer instanceof Answer.Text value) statement.setString(start, value.value());
        else if (answer instanceof Answer.Number value) statement.setDouble(start + 1, value.value());
        else if (answer instanceof Answer.BooleanValue value) statement.setInt(start + 2, value.value() ? 1 : 0);
        else if (answer instanceof Answer.DateValue value) statement.setString(start + 3, value.value().toString());
        else if (answer instanceof Answer.Choice value) statement.setString(start, String.join(CHOICE_SEPARATOR, value.values()));
    }

    private static String loadValueJson(Connection connection, UUID answerId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT value_text, value_number, value_boolean, value_date FROM answers WHERE id=?")) {
            statement.setString(1, answerId.toString());
            try (var row = statement.executeQuery()) {
                if (!row.next()) throw new SQLException("Answer no longer exists.");
                var text = row.getString(1); if (text != null) return "{\"type\":\"text\",\"value\":\"" + escape(text) + "\"}";
                if (row.getObject(2) != null) return "{\"type\":\"number\",\"value\":" + row.getDouble(2) + "}";
                if (row.getObject(3) != null) return "{\"type\":\"boolean\",\"value\":" + (row.getInt(3) == 1) + "}";
                var date = row.getString(4); return date == null ? "null" : "{\"type\":\"date\",\"value\":\"" + date + "\"}";
            }
        }
    }

    private static String answerJson(Answer answer) {
        if (answer instanceof Answer.Number value) return "{\"type\":\"number\",\"value\":" + value.value() + "}";
        if (answer instanceof Answer.BooleanValue value) return "{\"type\":\"boolean\",\"value\":" + value.value() + "}";
        if (answer instanceof Answer.DateValue value) return "{\"type\":\"date\",\"value\":\"" + value.value() + "\"}";
        if (answer instanceof Answer.Text value) return "{\"type\":\"text\",\"value\":\"" + escape(value.value()) + "\"}";
        var choice = (Answer.Choice) answer;
        return "{\"type\":\"choice\",\"value\":\"" + escape(String.join(CHOICE_SEPARATOR, choice.values())) + "\"}";
    }

    private static void bind(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int index = 0; index < params.size(); index++) {
            var value = params.get(index);
            if (value instanceof UUID id) statement.setString(index + 1, id.toString());
            else if (value instanceof Integer number) statement.setInt(index + 1, number);
            else statement.setString(index + 1, value.toString());
        }
    }

    private static UUID parseUuid(String value) {
        if (value == null) return null;
        try { return UUID.fromString(value); } catch (IllegalArgumentException ignored) { return null; }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
