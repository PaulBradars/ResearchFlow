package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.command.ReviewCommand;
import researchflow.domain.QualityIssueStatus;
import researchflow.domain.QualityIssueType;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;
import researchflow.service.DatasetCorrectionService;
import researchflow.service.FormService;
import researchflow.service.QualityReviewService;
import researchflow.service.QualityService;
import researchflow.service.ResponseSubmissionService;
import researchflow.service.StudyService;
import researchflow.service.ValidationException;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QualityReviewFailurePathsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void deferCommandMarksIssueDeferred() {
        var fixture = fixture();
        var issue = duplicateIssue(fixture);

        var deferred = fixture.review().apply(new ReviewCommand.Defer(issue.id(), "Need to check with the respondent first"));

        assertEquals(QualityIssueStatus.DEFERRED, deferred.status());
    }

    @Test
    void acceptWithTooShortNoteFailsValidation() {
        var fixture = fixture();
        var issue = duplicateIssue(fixture);

        assertThrows(ValidationException.class,
                () -> fixture.review().apply(new ReviewCommand.Accept(issue.id(), "ok")));
    }

    @Test
    void deferWithBlankNoteFailsValidation() {
        var fixture = fixture();
        var issue = duplicateIssue(fixture);

        assertThrows(ValidationException.class,
                () -> fixture.review().apply(new ReviewCommand.Defer(issue.id(), "")));
    }

    @Test
    void excludeWithTooShortReasonFailsValidation() {
        var fixture = fixture();
        var issue = duplicateIssue(fixture);

        assertThrows(ValidationException.class, () -> fixture.review().apply(
                new ReviewCommand.Exclude(issue.id(), fixture.studyId(), issue.responseId(), "no")));
    }

    @Test
    void forcedAuditFailureRollsBackExcludeWithoutPartialChange() throws Exception {
        var fixture = fixture();
        var issue = duplicateIssue(fixture);

        try (var connection = fixture.connections().open(); var statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER fail_audit BEFORE INSERT ON audit_logs BEGIN SELECT RAISE(ABORT, 'forced'); END");
        }

        assertThrows(PersistenceException.class, () -> fixture.review().apply(
                new ReviewCommand.Exclude(issue.id(), fixture.studyId(), issue.responseId(), "Response looked incomplete")));

        try (var connection = fixture.connections().open();
             var statement = connection.prepareStatement("SELECT status FROM responses WHERE id=?")) {
            statement.setString(1, issue.responseId().toString());
            try (var rows = statement.executeQuery()) {
                rows.next();
                assertNotEquals("EXCLUDED", rows.getString(1));
            }
        }

        var stillOpen = fixture.quality().list(fixture.studyId(), QualityIssueStatus.OPEN);
        assertEquals(1, stillOpen.stream().filter(open -> open.id().equals(issue.id())).count());
    }

    private researchflow.domain.QualityIssue duplicateIssue(Fixture fixture) {
        return fixture.quality().scan(fixture.studyId()).stream()
                .filter(issue -> issue.type() == QualityIssueType.DUPLICATE_RESPONSE)
                .findFirst().orElseThrow();
    }

    private Fixture fixture() {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var study = new StudyService(new JdbcStudyRepository(connections, transactions))
                .create("Quality Failure Study", "", "", "", null, null, List.of());
        var formRepository = new JdbcFormRepository(connections, transactions);
        var forms = new FormService(formRepository);
        var form = forms.create(study.id(), "Intake", "");
        var name = Question.create("name", "Name", "", QuestionType.SHORT_TEXT, true, null, null, List.of());
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(name))));
        form = forms.activate(form.id());
        var responseRepository = new JdbcResponseRepository(connections, transactions);
        var submissions = new ResponseSubmissionService(formRepository, responseRepository);
        submissions.submit(form.id(), Instant.now().minusSeconds(60), Map.of(name.id(), "Same Answer"));
        submissions.submit(form.id(), Instant.now().minusSeconds(30), Map.of(name.id(), "Same Answer"));
        var qualityRepository = new JdbcQualityRepository(connections, transactions);
        var quality = new QualityService(formRepository, responseRepository, qualityRepository);
        var datasetRepository = new JdbcDatasetRepository(connections, transactions);
        var corrections = new DatasetCorrectionService(datasetRepository, formRepository);
        var review = new QualityReviewService(qualityRepository, corrections);
        return new Fixture(connections, study.id(), quality, review);
    }

    private record Fixture(ConnectionFactory connections, UUID studyId, QualityService quality, QualityReviewService review) { }
}

