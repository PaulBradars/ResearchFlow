package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.domain.DatasetFilterOperator;
import researchflow.domain.DatasetQuery;
import researchflow.domain.DatasetSort;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;
import researchflow.service.DatasetCorrectionService;
import researchflow.service.DatasetService;
import researchflow.service.FormService;
import researchflow.service.ResponseSubmissionService;
import researchflow.service.StudyService;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcDatasetRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void boundedDatasetSupportsSearchTypedFilterSortDetailsAndAuditedCorrection() throws Exception {
        var fixture = fixture();
        var firstPage = fixture.datasets().query(fixture.studyId(), new DatasetQuery(null, "", null, null, "",
                DatasetSort.NEWEST, null, 0, 1));
        assertEquals(2, firstPage.totalRows());
        assertEquals(1, firstPage.rows().size());
        assertEquals(2, firstPage.variables().size());

        var searched = fixture.datasets().query(fixture.studyId(), new DatasetQuery(null, "ada lovelace", null, null, "",
                DatasetSort.NEWEST, null, 0, 50));
        assertEquals(1, searched.totalRows());
        assertEquals("Ada Lovelace", searched.rows().getFirst().cell(fixture.name().id()).displayValue());

        var filtered = fixture.datasets().query(fixture.studyId(), new DatasetQuery(null, "", fixture.age().id(),
                DatasetFilterOperator.GREATER_THAN, "25", DatasetSort.VARIABLE_DESC, fixture.age().id(), 0, 50));
        assertEquals(1, filtered.totalRows());
        assertEquals("36.0", filtered.rows().getFirst().cell(fixture.age().id()).displayValue());
        assertEquals(filtered.rows().getFirst().responseId(),
                fixture.datasets().detail(fixture.studyId(), filtered.rows().getFirst().responseId()).responseId());

        var adaResponse = searched.rows().getFirst().responseId();
        fixture.corrections().correct(fixture.studyId(), adaResponse, fixture.age().id(), "26", "Corrected from source sheet");
        var corrected = fixture.datasets().detail(fixture.studyId(), adaResponse);
        assertEquals("26.0", corrected.cell(fixture.age().id()).displayValue());

        try (var connection = fixture.connections().open()) {
            assertEquals(1, count(connection, "answer_corrections"));
            try (var statement = connection.prepareStatement(
                    "SELECT details_json FROM audit_logs WHERE event_type='ANSWER_CORRECTED'")) {
                try (var rows = statement.executeQuery()) {
                    assertTrue(rows.next()); assertTrue(rows.getString(1).contains("Corrected from source sheet"));
                    assertTrue(!rows.getString(1).contains("26.0"));
                }
            }
        }
        assertTrue(fixture.repository().findByStudy(fixture.studyId(), 250).stream()
                .anyMatch(event -> event.eventType().equals("ANSWER_CORRECTED")));
    }

    @Test
    void correctionAndHistoryRollBackWhenAuditInsertFails() throws Exception {
        var fixture = fixture();
        var page = fixture.datasets().query(fixture.studyId(), DatasetQuery.firstPage());
        var response = page.rows().stream().filter(row -> row.cell(fixture.name().id()).displayValue().equals("Ada Lovelace"))
                .findFirst().orElseThrow();
        try (var connection = fixture.connections().open(); var statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TRIGGER fail_correction_audit BEFORE INSERT ON audit_logs
                    WHEN NEW.event_type='ANSWER_CORRECTED'
                    BEGIN SELECT RAISE(ABORT, 'forced audit failure'); END
                    """);
        }
        assertThrows(PersistenceException.class, () -> fixture.corrections().correct(fixture.studyId(),
                response.responseId(), fixture.age().id(), "27", "Attempted correction"));
        assertEquals("24.0", fixture.datasets().detail(fixture.studyId(), response.responseId())
                .cell(fixture.age().id()).displayValue());
        try (var connection = fixture.connections().open()) { assertEquals(0, count(connection, "answer_corrections")); }
    }

    private Fixture fixture() {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var study = new StudyService(new JdbcStudyRepository(connections, transactions))
                .create("Dataset Study", "", "", "", null, null, List.of());
        var formRepository = new JdbcFormRepository(connections, transactions);
        var forms = new FormService(formRepository);
        var form = forms.create(study.id(), "Intake", "");
        var name = Question.create("name", "Name", "", QuestionType.SHORT_TEXT, true, null, null, List.of());
        var age = Question.create("age", "Age", "", QuestionType.NUMBER, true, 18d, 100d, List.of());
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(name, age))));
        form = forms.activate(form.id());
        var responses = new JdbcResponseRepository(connections, transactions);
        var submissions = new ResponseSubmissionService(formRepository, responses);
        submissions.submit(form.id(), Instant.now(), Map.of(name.id(), "Ada Lovelace", age.id(), "24"));
        submissions.submit(form.id(), Instant.now(), Map.of(name.id(), "Grace", age.id(), "36"));
        var repository = new JdbcDatasetRepository(connections, transactions);
        return new Fixture(connections, study.id(), name, age, repository,
                new DatasetService(repository, formRepository), new DatasetCorrectionService(repository, formRepository));
    }

    private static int count(java.sql.Connection connection, String table) throws Exception {
        try (var rows = connection.createStatement().executeQuery("SELECT COUNT(*) FROM " + table)) {
            rows.next(); return rows.getInt(1);
        }
    }

    private record Fixture(ConnectionFactory connections, java.util.UUID studyId, Question name, Question age,
                           JdbcDatasetRepository repository, DatasetService datasets,
                           DatasetCorrectionService corrections) { }
}

