package researchflow.persistence;

import researchflow.domain.Form;
import researchflow.domain.FormStatus;
import researchflow.domain.Question;
import researchflow.domain.QuestionOption;
import researchflow.domain.QuestionType;
import researchflow.domain.Section;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcFormRepository implements FormRepository {
    private final ConnectionFactory connections;
    private final TransactionManager transactions;

    public JdbcFormRepository(ConnectionFactory connections, TransactionManager transactions) {
        this.connections = connections;
        this.transactions = transactions;
    }

    @Override
    public void save(Form form, String auditEventType) {
        transactions.inTransaction(connection -> {
            upsertForm(connection, form);

            if ("FORM_CREATED".equals(auditEventType) || "FORM_UPDATED".equals(auditEventType)) {
                replaceStructure(connection, form);
            }
            insertAudit(connection, form, auditEventType);
            return null;
        });
    }

    @Override
    public Optional<Form> findById(UUID id) {
        try (var connection = connections.open();
             var statement = connection.prepareStatement("SELECT * FROM forms WHERE id = ?")) {
            statement.setString(1, id.toString());
            try (var results = statement.executeQuery()) {
                return results.next() ? Optional.of(mapForm(connection, results)) : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load the form.", exception);
        }
    }

    @Override
    public List<Form> findByStudy(UUID studyId) {
        try (var connection = connections.open();
             var statement = connection.prepareStatement(
                     "SELECT * FROM forms WHERE study_id = ? ORDER BY updated_at DESC")) {
            statement.setString(1, studyId.toString());
            try (var results = statement.executeQuery()) {
                var forms = new ArrayList<Form>();
                while (results.next()) forms.add(mapForm(connection, results));
                return List.copyOf(forms);
            }
        } catch (SQLException exception) {
            throw new PersistenceException("Could not load forms.", exception);
        }
    }

    private static void upsertForm(Connection connection, Form form) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO forms(id, study_id, title, description, status, version, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(id) DO UPDATE SET title=excluded.title, description=excluded.description,
                    status=excluded.status, version=excluded.version, updated_at=excluded.updated_at
                """)) {
            statement.setString(1, form.id().toString());
            statement.setString(2, form.studyId().toString());
            statement.setString(3, form.title());
            statement.setString(4, form.description());
            statement.setString(5, form.status().name());
            statement.setInt(6, form.version());
            statement.setString(7, form.createdAt().toString());
            statement.setString(8, form.updatedAt().toString());
            statement.executeUpdate();
        }
    }

    private static void replaceStructure(Connection connection, Form form) throws SQLException {
        try (var delete = connection.prepareStatement("DELETE FROM form_sections WHERE form_id = ?")) {
            delete.setString(1, form.id().toString());
            delete.executeUpdate();
        }
        var sectionSql = "INSERT INTO form_sections(id, form_id, title, description, position) VALUES (?, ?, ?, ?, ?)";
        var questionSql = """
                INSERT INTO questions(id, form_id, section_id, variable_key, label, help_text, question_type,
                                      required, position, configuration_json, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        var optionSql = "INSERT INTO question_options(id, question_id, option_value, label, position) VALUES (?, ?, ?, ?, ?)";
        try (var sectionInsert = connection.prepareStatement(sectionSql);
             var questionInsert = connection.prepareStatement(questionSql);
             var optionInsert = connection.prepareStatement(optionSql)) {
            int questionPosition = 0;
            for (int sectionPosition = 0; sectionPosition < form.sections().size(); sectionPosition++) {
                var section = form.sections().get(sectionPosition);
                sectionInsert.setString(1, section.id().toString());
                sectionInsert.setString(2, form.id().toString());
                sectionInsert.setString(3, section.title());
                sectionInsert.setString(4, section.description());
                sectionInsert.setInt(5, sectionPosition);
                sectionInsert.executeUpdate();
                for (var question : section.questions()) {
                    questionInsert.setString(1, question.id().toString());
                    questionInsert.setString(2, form.id().toString());
                    questionInsert.setString(3, section.id().toString());
                    questionInsert.setString(4, question.variableKey());
                    questionInsert.setString(5, question.label());
                    questionInsert.setString(6, question.helpText());
                    questionInsert.setString(7, question.type().name());
                    questionInsert.setInt(8, question.required() ? 1 : 0);
                    questionInsert.setInt(9, questionPosition++);
                    questionInsert.setString(10, configuration(question));
                    questionInsert.setString(11, question.createdAt().toString());
                    questionInsert.setString(12, question.updatedAt().toString());
                    questionInsert.executeUpdate();
                    for (int optionPosition = 0; optionPosition < question.options().size(); optionPosition++) {
                        var option = question.options().get(optionPosition);
                        optionInsert.setString(1, option.id().toString());
                        optionInsert.setString(2, question.id().toString());
                        optionInsert.setString(3, option.value());
                        optionInsert.setString(4, option.label());
                        optionInsert.setInt(5, optionPosition);
                        optionInsert.executeUpdate();
                    }
                }
            }
        }
    }

    private static Form mapForm(Connection connection, ResultSet row) throws SQLException {
        var formId = UUID.fromString(row.getString("id"));
        return new Form(formId, UUID.fromString(row.getString("study_id")), row.getString("title"),
                row.getString("description"), FormStatus.valueOf(row.getString("status")), row.getInt("version"),
                loadSections(connection, formId), Instant.parse(row.getString("created_at")),
                Instant.parse(row.getString("updated_at")));
    }

    private static List<Section> loadSections(Connection connection, UUID formId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM form_sections WHERE form_id = ? ORDER BY position")) {
            statement.setString(1, formId.toString());
            try (var rows = statement.executeQuery()) {
                var sections = new ArrayList<Section>();
                while (rows.next()) {
                    var sectionId = UUID.fromString(rows.getString("id"));
                    sections.add(new Section(sectionId, rows.getString("title"), rows.getString("description"),
                            loadQuestions(connection, formId, sectionId)));
                }
                return List.copyOf(sections);
            }
        }
    }

    private static List<Question> loadQuestions(Connection connection, UUID formId, UUID sectionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM questions WHERE form_id = ? AND section_id = ? ORDER BY position")) {
            statement.setString(1, formId.toString());
            statement.setString(2, sectionId.toString());
            try (var rows = statement.executeQuery()) {
                var questions = new ArrayList<Question>();
                while (rows.next()) {
                    var id = UUID.fromString(rows.getString("id"));
                    var config = rows.getString("configuration_json");
                    questions.add(new Question(id, rows.getString("variable_key"), rows.getString("label"),
                            rows.getString("help_text"), QuestionType.valueOf(rows.getString("question_type")),
                            rows.getInt("required") == 1, configNumber(config, "min"), configNumber(config, "max"),
                            loadOptions(connection, id), Instant.parse(rows.getString("created_at")),
                            Instant.parse(rows.getString("updated_at"))));
                }
                return List.copyOf(questions);
            }
        }
    }

    private static List<QuestionOption> loadOptions(Connection connection, UUID questionId) throws SQLException {
        try (var statement = connection.prepareStatement(
                "SELECT * FROM question_options WHERE question_id = ? ORDER BY position")) {
            statement.setString(1, questionId.toString());
            try (var rows = statement.executeQuery()) {
                var options = new ArrayList<QuestionOption>();
                while (rows.next()) options.add(new QuestionOption(UUID.fromString(rows.getString("id")),
                        rows.getString("option_value"), rows.getString("label")));
                return List.copyOf(options);
            }
        }
    }

    private static String configuration(Question question) {
        var min = question.minimum() == null ? "null" : question.minimum().toString();
        var max = question.maximum() == null ? "null" : question.maximum().toString();
        return "{\"min\":" + min + ",\"max\":" + max + "}";
    }

    private static Double configNumber(String json, String key) {
        if (json == null) return null;
        var matcher = java.util.regex.Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*(-?[0-9]+(?:\\.[0-9]+)?)").matcher(json);
        return matcher.find() ? Double.valueOf(matcher.group(1)) : null;
    }

    private static void insertAudit(Connection connection, Form form, String eventType) throws SQLException {
        try (var statement = connection.prepareStatement("""
                INSERT INTO audit_logs(id, study_id, event_type, entity_type, entity_id, actor, occurred_at, details_json)
                VALUES (?, ?, ?, 'FORM', ?, 'local-researcher', ?, '{}')
                """)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, form.studyId().toString());
            statement.setString(3, eventType);
            statement.setString(4, form.id().toString());
            statement.setString(5, Instant.now().toString());
            statement.executeUpdate();
        }
    }
}

