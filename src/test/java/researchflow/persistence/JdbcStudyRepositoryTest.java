package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.domain.StudyStatus;
import researchflow.service.StudyService;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcStudyRepositoryTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void studyCrudQuestionsAuditAndArchivePersistAcrossConnections() throws Exception {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var repository = new JdbcStudyRepository(connections, new TransactionManager(connections));
        var service = new StudyService(repository);

        var created = service.create("  Focus Study  ", "Initial description", "Measure focus", "A. Researcher",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 1), List.of("How focused are students?"));
        assertEquals("Focus Study", created.title());
        assertEquals(1, service.list(false).size());

        service.update(created.id(), "Focus Study 2026", "Updated", "Measure focus", "A. Researcher",
                created.startDate(), created.endDate(), List.of("Does sleep relate to focus?", "Does time matter?"));
        var reloaded = service.find(created.id()).orElseThrow();
        assertEquals("Focus Study 2026", reloaded.title());
        assertEquals(List.of("Does sleep relate to focus?", "Does time matter?"), reloaded.researchQuestions());

        service.archive(created.id());
        assertFalse(service.list(false).stream().anyMatch(study -> study.id().equals(created.id())));
        assertEquals(StudyStatus.ARCHIVED, service.find(created.id()).orElseThrow().status());

        try (var connection = connections.open();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM audit_logs WHERE study_id = ?")) {
            statement.setString(1, created.id().toString());
            try (var results = statement.executeQuery()) {
                results.next();
                assertEquals(3, results.getInt(1));
            }
        }

        var reopenedConnections = new ConnectionFactory(temporaryDirectory.resolve("researchflow-test.db"));
        var reopened = new StudyService(new JdbcStudyRepository(reopenedConnections,
                new TransactionManager(reopenedConnections)));
        assertTrue(reopened.find(created.id()).isPresent());
    }
}

