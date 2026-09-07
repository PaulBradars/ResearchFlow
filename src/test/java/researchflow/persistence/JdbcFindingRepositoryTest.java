package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.FindingStatus;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;
import researchflow.service.AnalysisService;
import researchflow.service.FindingService;
import researchflow.service.FormService;
import researchflow.service.ResponseSubmissionService;
import researchflow.service.StudyService;
import researchflow.visualization.ChartSpec;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcFindingRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void createsEditsApprovesAndRoundTripsAPersistedChart() {
        var fixture = fixture();
        var evidence = fixture.analysisService().run(fixture.studyId(),
                new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, fixture.focus().id(), null, List.of(), null));
        var chart = new ChartSpec.Bar("Focus", "Category", "Count", List.of("A", "B"), List.of(2.0, 3.0));

        var finding = fixture.findingService().draft(fixture.studyId(), evidence, "Focus averages around 3.5.", chart);
        assertEquals(FindingStatus.DRAFT, finding.status());
        assertEquals(evidence.id(), finding.analysisId());
        assertEquals(evidence.datasetVersionId(), finding.datasetVersionId());
        assertEquals(chart, finding.chart());

        var edited = fixture.findingService().edit(finding.id(), "Focus averages around 3.5 across all respondents.");
        assertEquals(evidence.id(), edited.analysisId()); // wording changes never move the analysis/version link
        assertEquals(evidence.datasetVersionId(), edited.datasetVersionId());
        assertEquals("Focus averages around 3.5 across all respondents.", edited.text());

        var approved = fixture.findingService().approve(finding.id());
        assertEquals(FindingStatus.APPROVED, approved.status());
        assertNotNull(approved.approvedAt());

        assertTrue(fixture.auditRepository().findByStudy(fixture.studyId(), 250).stream()
                .anyMatch(event -> event.eventType().equals("FINDING_APPROVED")));
    }

    @Test
    void rejectingAFindingWithNoChartKeepsItOutOfTheApprovedSet() {
        var fixture = fixture();
        var evidence = fixture.analysisService().run(fixture.studyId(),
                new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, fixture.focus().id(), null, List.of(), null));
        var finding = fixture.findingService().draft(fixture.studyId(), evidence, "A draft finding about focus.", null);

        var rejected = fixture.findingService().reject(finding.id());

        assertEquals(FindingStatus.REJECTED, rejected.status());
        assertNull(rejected.chart());
        assertTrue(fixture.findingService().list(fixture.studyId()).stream()
                .noneMatch(value -> value.status() == FindingStatus.APPROVED));
    }

    private Fixture fixture() {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var study = new StudyService(new JdbcStudyRepository(connections, transactions))
                .create("Findings Study", "", "", "", null, null, List.of());
        var formRepository = new JdbcFormRepository(connections, transactions);
        var forms = new FormService(formRepository);
        var form = forms.create(study.id(), "Survey", "");
        var focus = Question.create("focus", "Focus", "", QuestionType.NUMBER, true, 0d, 10d, List.of());
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(focus))));
        form = forms.activate(form.id());
        var responseRepository = new JdbcResponseRepository(connections, transactions);
        var submissions = new ResponseSubmissionService(formRepository, responseRepository);
        submissions.submit(form.id(), Instant.now().minusSeconds(30), Map.of(focus.id(), "3"));
        submissions.submit(form.id(), Instant.now().minusSeconds(30), Map.of(focus.id(), "4"));

        var versionRepository = new JdbcVersionRepository(connections, transactions);
        var analysisRepository = new JdbcAnalysisRepository(connections, transactions);
        var analysisService = new AnalysisService(formRepository, versionRepository, analysisRepository);
        var findingRepository = new JdbcFindingRepository(connections, transactions);
        var findingService = new FindingService(findingRepository);
        var datasetRepository = new JdbcDatasetRepository(connections, transactions);
        return new Fixture(study.id(), focus, analysisService, findingService, datasetRepository);
    }

    private record Fixture(UUID studyId, Question focus, AnalysisService analysisService,
                           FindingService findingService, AuditRepository auditRepository) { }
}
