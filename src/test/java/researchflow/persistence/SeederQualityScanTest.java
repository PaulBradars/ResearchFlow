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

class SeederQualityScanTest {
    @TempDir Path temporaryDirectory;

    @Test
    void seededDevelopmentStudySurfacesItsEmbeddedQualityExamples() {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var formRepository = new JdbcFormRepository(connections, transactions);
        var responseRepository = new JdbcResponseRepository(connections, transactions);
        var studyService = new StudyService(new JdbcStudyRepository(connections, transactions));
        var forms = new FormService(formRepository);
        var submissions = new ResponseSubmissionService(formRepository, responseRepository);
        new Seeder(studyService, forms, submissions).seedIfEmpty();

        var study = studyService.list(true).getFirst();
        var quality = new QualityService(formRepository, responseRepository, new JdbcQualityRepository(connections, transactions));
        var issues = quality.scan(study.id());

        var types = issues.stream().map(researchflow.domain.QualityIssue::type).collect(java.util.stream.Collectors.toSet());
        assertTrue(types.contains(QualityIssueType.DUPLICATE_RESPONSE), "expected the seeded duplicate pair to be flagged");
        assertTrue(types.contains(QualityIssueType.OUTLIER), "expected the seeded 14-hour sleep response to be flagged");
        assertTrue(types.contains(QualityIssueType.FAST_SUBMISSION), "expected the seeded 3-second response to be flagged");
        assertTrue(List.of(QualityIssueType.MISSING_REQUIRED, QualityIssueType.INVALID_RANGE).stream()
                .noneMatch(types::contains), "the seed's optional blanks and valid ranges must not be misflagged");
    }
}

