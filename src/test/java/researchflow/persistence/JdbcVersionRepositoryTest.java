package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;
import researchflow.service.DatasetCorrectionService;
import researchflow.service.FormService;
import researchflow.service.ResponseSubmissionService;
import researchflow.service.StudyService;
import researchflow.service.VersionService;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcVersionRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void createSnapshotCapturesCurrentAnswersAndTracksTheActiveVersion() throws Exception {
        var fixture = fixture();

        var version = fixture.versions().createSnapshot(fixture.studyId(), "Initial cleaning baseline", "First pass");
        assertEquals(1, version.versionNumber());
        assertTrue(version.active());
        assertNull(version.parentVersionId());
        assertEquals(version.id(), fixture.versions().active(fixture.studyId()).orElseThrow().id());

        try (var connection = fixture.connections().open();
             var statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM version_answer_snapshots WHERE dataset_version_id=?")) {
            statement.setString(1, version.id().toString());
            try (var rows = statement.executeQuery()) {
                rows.next();
                assertEquals(4, rows.getInt(1)); // 2 responses x 2 answered questions
            }
        }
    }

    @Test
    void restoreRewritesLiveDataAsANewVersionWithoutRewritingHistory() throws Exception {
        var fixture = fixture();
        var v1 = fixture.versions().createSnapshot(fixture.studyId(), "Before cleaning", "");

        fixture.corrections().correct(fixture.studyId(), fixture.adaResponseId(), fixture.age().id(), "26", "Typo fix");
        excludeResponse(fixture, fixture.graceResponseId());
        assertEquals("26.0", currentValue(fixture, fixture.adaResponseId(), fixture.age().id()));
        assertEquals("EXCLUDED", responseStatus(fixture, fixture.graceResponseId()));

        var v2 = fixture.versions().createSnapshot(fixture.studyId(), "After correction and exclusion", "Cleaning batch 1");
        assertEquals(2, v2.versionNumber());
        assertEquals(v1.id(), v2.parentVersionId());

        var v3 = fixture.versions().restore(fixture.studyId(), v1.id(), "Correction and exclusion were premature");
        assertEquals(3, v3.versionNumber());
        assertEquals(v2.id(), v3.parentVersionId());
        assertTrue(v3.reason().contains("Restored to version 1"));
        assertTrue(v3.active());

        // Restore rolls back to v1's snapshot: the correction is undone and the exclusion is lifted.
        assertEquals("24.0", currentValue(fixture, fixture.adaResponseId(), fixture.age().id()));
        assertEquals("COMPLETE", responseStatus(fixture, fixture.graceResponseId()));

        assertEquals(3, fixture.versions().list(fixture.studyId()).size());
        assertTrue(fixture.versions().list(fixture.studyId()).stream().anyMatch(v -> v.id().equals(v1.id())));
        assertTrue(fixture.versions().list(fixture.studyId()).stream().anyMatch(v -> v.id().equals(v2.id())));
    }

    private static void excludeResponse(Fixture fixture, UUID responseId) throws Exception {
        try (var connection = fixture.connections().open();
             var statement = connection.prepareStatement("UPDATE responses SET status='EXCLUDED' WHERE id=?")) {
            statement.setString(1, responseId.toString());
            statement.executeUpdate();
        }
    }

    private static String responseStatus(Fixture fixture, UUID responseId) throws Exception {
        try (var connection = fixture.connections().open();
             var statement = connection.prepareStatement("SELECT status FROM responses WHERE id=?")) {
            statement.setString(1, responseId.toString());
            try (var rows = statement.executeQuery()) { rows.next(); return rows.getString(1); }
        }
    }

    private static String currentValue(Fixture fixture, UUID responseId, UUID questionId) throws Exception {
        try (var connection = fixture.connections().open();
             var statement = connection.prepareStatement(
                     "SELECT value_number FROM answers WHERE response_id=? AND question_id=?")) {
            statement.setString(1, responseId.toString());
            statement.setString(2, questionId.toString());
            try (var rows = statement.executeQuery()) { rows.next(); return Double.toString(rows.getDouble(1)); }
        }
    }

    private Fixture fixture() {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var writeGuard = new researchflow.service.StudyWriteGuard(new JdbcStudyRepository(connections, transactions));
        var study = new StudyService(new JdbcStudyRepository(connections, transactions))
                .create("Version Study", "", "", "", null, null, List.of());
        var formRepository = new JdbcFormRepository(connections, transactions);
        var forms = new FormService(formRepository, writeGuard);
        var form = forms.create(study.id(), "Intake", "");
        var name = Question.create("name", "Name", "", QuestionType.SHORT_TEXT, true, null, null, List.of());
        var age = Question.create("age", "Age", "", QuestionType.NUMBER, true, 18d, 100d, List.of());
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(name, age))));
        form = forms.activate(form.id());
        var responseRepository = new JdbcResponseRepository(connections, transactions);
        var submissions = new ResponseSubmissionService(formRepository, responseRepository, writeGuard);
        var ada = submissions.submit(form.id(), Instant.now().minusSeconds(40), Map.of(name.id(), "Ada Lovelace", age.id(), "24"));
        var grace = submissions.submit(form.id(), Instant.now().minusSeconds(40), Map.of(name.id(), "Grace", age.id(), "36"));
        var versionRepository = new JdbcVersionRepository(connections, transactions);
        var versions = new VersionService(versionRepository, writeGuard);
        var datasetRepository = new JdbcDatasetRepository(connections, transactions);
        var corrections = new DatasetCorrectionService(datasetRepository, formRepository, writeGuard);
        return new Fixture(connections, study.id(), age, versions, corrections, ada.id(), grace.id());
    }

    private record Fixture(ConnectionFactory connections, UUID studyId, Question age, VersionService versions,
                           DatasetCorrectionService corrections, UUID adaResponseId, UUID graceResponseId) { }
}
