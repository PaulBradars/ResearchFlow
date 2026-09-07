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

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcQualityRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void scanDetectsMissingRequiredAndRangeIssuesThenReviewActionsResolveThem() throws Exception {
        var fixture = fixture();
        insertDirtyResponse(fixture);

        var issues = fixture.quality().scan(fixture.studyId());
        assertTrue(issues.stream().anyMatch(issue -> issue.type() == QualityIssueType.MISSING_REQUIRED
                && issue.responseId().equals(fixture.dirtyResponseId())));
        assertTrue(issues.stream().anyMatch(issue -> issue.type() == QualityIssueType.INVALID_RANGE
                && issue.responseId().equals(fixture.dirtyResponseId())));

        var rescanned = fixture.quality().scan(fixture.studyId());
        assertEquals(issues.size(), rescanned.size());
        assertEquals(issues.stream().map(researchflow.domain.QualityIssue::id).collect(java.util.stream.Collectors.toSet()),
                rescanned.stream().map(researchflow.domain.QualityIssue::id).collect(java.util.stream.Collectors.toSet()));

        var rangeIssue = issues.stream().filter(issue -> issue.type() == QualityIssueType.INVALID_RANGE).findFirst().orElseThrow();
        var resolved = fixture.review().apply(new ReviewCommand.Correct(rangeIssue.id(), fixture.studyId(),
                fixture.dirtyResponseId(), fixture.age().id(), "24", "Fixed from source sheet"));
        assertEquals(QualityIssueStatus.RESOLVED, resolved.status());

        var missingIssue = fixture.quality().list(fixture.studyId(), null).stream()
                .filter(issue -> issue.type() == QualityIssueType.MISSING_REQUIRED).findFirst().orElseThrow();
        var excluded = fixture.review().apply(new ReviewCommand.Exclude(missingIssue.id(), fixture.studyId(),
                fixture.dirtyResponseId(), "Response was incomplete"));
        assertEquals(QualityIssueStatus.RESOLVED, excluded.status());

        try (var connection = fixture.connections().open();
             var statement = connection.prepareStatement("SELECT status FROM responses WHERE id=?")) {
            statement.setString(1, fixture.dirtyResponseId().toString());
            try (var rows = statement.executeQuery()) { rows.next(); assertEquals("EXCLUDED", rows.getString(1)); }
        }
        assertTrue(fixture.auditRepository().findByStudy(fixture.studyId(), 250).stream()
                .anyMatch(event -> event.eventType().equals("RESPONSE_EXCLUDED")));

        // The excluded response no longer contributes new issues; only its resolved history remains.
        var afterExclusion = fixture.quality().scan(fixture.studyId());
        assertTrue(afterExclusion.stream()
                .filter(issue -> issue.responseId() != null && issue.responseId().equals(fixture.dirtyResponseId()))
                .allMatch(issue -> issue.status() == QualityIssueStatus.RESOLVED));

        assertThrows(IllegalArgumentException.class, () -> fixture.review().apply(
                new ReviewCommand.Accept(UUID.randomUUID(), "no such issue")));
    }

    @Test
    void acceptedDuplicateIsNotReReportedOnRescan() {
        var fixture = fixture();
        var startedAt = Instant.now().minusSeconds(120);
        fixture.submissions().submit(fixture.formId(), startedAt.minusSeconds(60),
                Map.of(fixture.name().id(), "Same Value", fixture.age().id(), "50"));
        fixture.submissions().submit(fixture.formId(), startedAt,
                Map.of(fixture.name().id(), "Same Value", fixture.age().id(), "50"));

        var issues = fixture.quality().scan(fixture.studyId());
        var duplicate = issues.stream().filter(issue -> issue.type() == QualityIssueType.DUPLICATE_RESPONSE)
                .findFirst().orElseThrow();

        var accepted = fixture.review().apply(new ReviewCommand.Accept(duplicate.id(), "Two genuinely separate submissions"));
        assertEquals(QualityIssueStatus.ACCEPTED, accepted.status());

        // Re-scanning must not create a second issue for the same still-present duplicate condition.
        var rescanned = fixture.quality().scan(fixture.studyId());
        assertEquals(1, rescanned.stream().filter(issue -> issue.type() == QualityIssueType.DUPLICATE_RESPONSE).count());
        assertEquals(1, fixture.quality().list(fixture.studyId(), QualityIssueStatus.ACCEPTED).size());
    }

    private void insertDirtyResponse(Fixture fixture) throws Exception {
        try (var connection = fixture.connections().open()) {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO responses(id, form_id, form_version, status, submitted_at, duration_seconds)
                    VALUES (?, ?, 1, 'COMPLETE', ?, 45)
                    """)) {
                statement.setString(1, fixture.dirtyResponseId().toString());
                statement.setString(2, fixture.formId().toString());
                statement.setString(3, Instant.now().toString());
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO answers(id, response_id, question_id, value_number, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, UUID.randomUUID().toString());
                statement.setString(2, fixture.dirtyResponseId().toString());
                statement.setString(3, fixture.age().id().toString());
                statement.setDouble(4, 150);
                statement.setString(5, Instant.now().toString());
                statement.setString(6, Instant.now().toString());
                statement.executeUpdate();
            }
        }
    }

    private Fixture fixture() {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var study = new StudyService(new JdbcStudyRepository(connections, transactions))
                .create("Quality Study", "", "", "", null, null, List.of());
        var formRepository = new JdbcFormRepository(connections, transactions);
        var forms = new FormService(formRepository);
        var form = forms.create(study.id(), "Intake", "");
        var name = Question.create("name", "Name", "", QuestionType.SHORT_TEXT, true, null, null, List.of());
        var age = Question.create("age", "Age", "", QuestionType.NUMBER, true, 18d, 100d, List.of());
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(name, age))));
        form = forms.activate(form.id());
        var responseRepository = new JdbcResponseRepository(connections, transactions);
        var submissions = new ResponseSubmissionService(formRepository, responseRepository);
        submissions.submit(form.id(), Instant.now().minusSeconds(40), Map.of(name.id(), "Ada Lovelace", age.id(), "24"));
        submissions.submit(form.id(), Instant.now().minusSeconds(40), Map.of(name.id(), "Grace", age.id(), "36"));
        var qualityRepository = new JdbcQualityRepository(connections, transactions);
        var quality = new QualityService(formRepository, responseRepository, qualityRepository);
        var datasetRepository = new JdbcDatasetRepository(connections, transactions);
        var corrections = new DatasetCorrectionService(datasetRepository, formRepository);
        var review = new QualityReviewService(qualityRepository, corrections);
        return new Fixture(connections, study.id(), form.id(), name, age, UUID.randomUUID(), submissions,
                quality, review, datasetRepository);
    }

    private record Fixture(ConnectionFactory connections, UUID studyId, UUID formId, Question name, Question age,
                           UUID dirtyResponseId, ResponseSubmissionService submissions, QualityService quality,
                           QualityReviewService review, AuditRepository auditRepository) { }
}
