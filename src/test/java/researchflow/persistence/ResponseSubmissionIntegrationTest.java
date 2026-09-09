package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.domain.Question;
import researchflow.domain.QuestionOption;
import researchflow.domain.QuestionType;
import researchflow.service.FormService;
import researchflow.service.ResponseSubmissionService;
import researchflow.service.StudyService;
import researchflow.service.ValidationException;

import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResponseSubmissionIntegrationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void validatesTypedAnswersAndStoresResponseAnswersAndAuditAtomically() throws Exception {
        var fixture = fixture();
        var raw = new LinkedHashMap<java.util.UUID, String>();
        raw.put(fixture.number().id(), "24");
        raw.put(fixture.choice().id(), "yes");
        var response = fixture.submissions().submit(fixture.formId(), Instant.now().minusSeconds(5), raw);

        try (var connection = fixture.connections().open()) {
            assertEquals(1, count(connection, "responses", response.id().toString(), "id"));
            assertEquals(2, count(connection, "answers", response.id().toString(), "response_id"));
            assertEquals(1, count(connection, "audit_logs", response.id().toString(), "entity_id"));
        }

        raw.put(fixture.number().id(), "12");
        assertThrows(ValidationException.class,
                () -> fixture.submissions().submit(fixture.formId(), Instant.now(), raw));

        // Closing after collection must not rewrite answer-referenced questions.
        fixture.forms().close(fixture.formId());
    }

    @Test
    void forcedAnswerInsertFailureRollsBackResponseAndAudit() throws Exception {
        var fixture = fixture();
        try (var connection = fixture.connections().open(); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER fail_answer BEFORE INSERT ON answers BEGIN SELECT RAISE(ABORT, 'forced'); END");
        }
        var raw = new LinkedHashMap<java.util.UUID, String>();
        raw.put(fixture.number().id(), "24");
        raw.put(fixture.choice().id(), "yes");
        assertThrows(PersistenceException.class,
                () -> fixture.submissions().submit(fixture.formId(), Instant.now(), raw));
        try (var connection = fixture.connections().open(); var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM responses")) {
            rows.next(); assertEquals(0, rows.getInt(1));
        }
    }

    private Fixture fixture() {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var writeGuard = new researchflow.service.StudyWriteGuard(new JdbcStudyRepository(connections, transactions));
        var study = new StudyService(new JdbcStudyRepository(connections, transactions))
                .create("Study", "", "", "", null, null, List.of());
        var formRepository = new JdbcFormRepository(connections, transactions);
        var forms = new FormService(formRepository, writeGuard);
        var form = forms.create(study.id(), "Intake", "");
        var number = Question.create("age", "Age", "", QuestionType.NUMBER, true, 18d, 100d, List.of());
        var choice = Question.create("consent", "Consent", "", QuestionType.SINGLE_CHOICE, true, null, null,
                List.of(QuestionOption.create("yes"), QuestionOption.create("no")));
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(number, choice))));
        form = forms.activate(form.id());
        var responseRepository = new JdbcResponseRepository(connections, transactions);
        return new Fixture(connections, form.id(), number, choice, forms,
                new ResponseSubmissionService(formRepository, responseRepository, writeGuard));
    }

    private static int count(java.sql.Connection connection, String table, String value, String column) throws Exception {
        try (var statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?")) {
            statement.setString(1, value);
            try (var rows = statement.executeQuery()) { rows.next(); return rows.getInt(1); }
        }
    }

    private record Fixture(ConnectionFactory connections, java.util.UUID formId, Question number,
                           Question choice, FormService forms, ResponseSubmissionService submissions) { }
}
