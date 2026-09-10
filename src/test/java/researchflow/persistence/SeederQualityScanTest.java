package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.domain.QualityIssueType;
import researchflow.service.FormService;
import researchflow.service.QualityService;
import researchflow.service.ResponseSubmissionService;
import researchflow.service.StudyService;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Phase 3 development seed deliberately embeds one duplicate pair, one statistical outlier,
 * and one unusually fast submission (see PHASE_3_HANDOFF.md). This confirms the Phase 4 handler
 * chain actually surfaces them, without inventing a separate fixture.
 */
class SeederQualityScanTest {
    @TempDir Path temporaryDirectory;

    @Test
    void seededDevelopmentStudySurfacesItsEmbeddedQualityExamples() {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var writeGuard = new researchflow.service.StudyWriteGuard(new JdbcStudyRepository(connections, transactions));
        var formRepository = new JdbcFormRepository(connections, transactions);
        var responseRepository = new JdbcResponseRepository(connections, transactions);
        var studyService = new StudyService(new JdbcStudyRepository(connections, transactions));
        var forms = new FormService(formRepository, writeGuard);
        var submissions = new ResponseSubmissionService(formRepository, responseRepository, writeGuard);
        new Seeder(studyService, forms, submissions).seedIfEmpty();

        var study = studyService.list(true).getFirst();
        org.junit.jupiter.api.Assertions.assertEquals(150, responseRepository.findFullByStudy(study.id()).size());
        assertTrue(forms.list(study.id()).getFirst().questions().stream()
                .anyMatch(question -> question.variableKey().equals("screen_time_hours")));
        new Seeder(studyService, forms, submissions).seedIfEmpty();
        org.junit.jupiter.api.Assertions.assertEquals(150, responseRepository.findFullByStudy(study.id()).size(),
                "Restarting must not duplicate the expanded demo data");
        var quality = new QualityService(formRepository, responseRepository, new JdbcQualityRepository(connections, transactions), writeGuard);
        var issues = quality.scan(study.id());

        var types = issues.stream().map(researchflow.domain.QualityIssue::type).collect(java.util.stream.Collectors.toSet());
        assertTrue(types.contains(QualityIssueType.DUPLICATE_RESPONSE), "expected the seeded duplicate pair to be flagged");
        assertTrue(types.contains(QualityIssueType.OUTLIER), "expected the seeded 14-hour sleep response to be flagged");
        assertTrue(types.contains(QualityIssueType.FAST_SUBMISSION), "expected the seeded 3-second response to be flagged");
        assertTrue(List.of(QualityIssueType.MISSING_REQUIRED, QualityIssueType.INVALID_RANGE).stream()
                .noneMatch(types::contains), "the seed's optional blanks and valid ranges must not be misflagged");
    }
}
