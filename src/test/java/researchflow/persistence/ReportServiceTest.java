package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;
import researchflow.service.AnalysisService;
import researchflow.service.AuditService;
import researchflow.service.FindingService;
import researchflow.service.FormService;
import researchflow.service.QualityService;
import researchflow.service.ReportService;
import researchflow.service.ResponseQueryService;
import researchflow.service.ResponseSubmissionService;
import researchflow.service.StudyService;
import researchflow.service.VersionService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void composesAReportWithStudySummaryAndOnlyApprovedFindings() {
        var fixture = fixture();
        var evidence = fixture.analysisService().run(fixture.studyId(),
                new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, fixture.focus().id(), null, List.of(), null));
        var approved = fixture.findingService().draft(fixture.studyId(), evidence, "Focus is moderate overall.", null);
        fixture.findingService().approve(approved.id());
        var draftOnly = fixture.findingService().draft(fixture.studyId(), evidence, "An unreviewed draft finding.", null);

        var document = fixture.reportService().compose(fixture.studyId());

        assertEquals(1, document.formCount());
        assertEquals(2, document.responseCount());
        assertEquals(1, document.approvedFindings().size());
        assertEquals("Focus is moderate overall.", document.approvedFindings().getFirst().text());
        assertTrue(document.approvedFindings().stream().noneMatch(finding -> finding.text().equals(draftOnly.text())));
        assertFalse(document.limitations().isEmpty());
    }

    @Test
    void exportsASelfContainedHtmlFileAndRecordsAnAuditEvent() throws Exception {
        var fixture = fixture();
        var target = temporaryDirectory.resolve("report.html");

        var written = fixture.reportService().export(fixture.studyId(), target);

        assertEquals(target, written);
        var html = Files.readString(target);
        assertTrue(html.startsWith("<!doctype html>"));
        assertTrue(html.contains(fixture.studyTitle()));
        assertTrue(fixture.auditService().timeline(fixture.studyId()).stream()
                .anyMatch(event -> event.eventType().equals("REPORT_GENERATED")));
    }

    private Fixture fixture() {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var writeGuard = new researchflow.service.StudyWriteGuard(new JdbcStudyRepository(connections, transactions));
        var studyTitle = "Report Study";
        var studyService = new StudyService(new JdbcStudyRepository(connections, transactions));
        var study = studyService.create(studyTitle, "", "", "", null, null, List.of());
        var formRepository = new JdbcFormRepository(connections, transactions);
        var forms = new FormService(formRepository, writeGuard);
        var form = forms.create(study.id(), "Survey", "");
        var focus = Question.create("focus", "Focus", "", QuestionType.NUMBER, true, 0d, 10d, List.of());
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(focus))));
        form = forms.activate(form.id());
        var responseRepository = new JdbcResponseRepository(connections, transactions);
        var submissions = new ResponseSubmissionService(formRepository, responseRepository, writeGuard);
        submissions.submit(form.id(), Instant.now().minusSeconds(30), Map.of(focus.id(), "3"));
        submissions.submit(form.id(), Instant.now().minusSeconds(30), Map.of(focus.id(), "4"));
        var responses = new ResponseQueryService(responseRepository);

        var datasetRepository = new JdbcDatasetRepository(connections, transactions);
        var audits = new AuditService(datasetRepository, writeGuard);
        var qualityRepository = new JdbcQualityRepository(connections, transactions);
        var quality = new QualityService(formRepository, responseRepository, qualityRepository, writeGuard);
        var versionRepository = new JdbcVersionRepository(connections, transactions);
        var versions = new VersionService(versionRepository, writeGuard);
        var analysisRepository = new JdbcAnalysisRepository(connections, transactions);
        var analysisService = new AnalysisService(formRepository, versionRepository, analysisRepository, writeGuard);
        var findingRepository = new JdbcFindingRepository(connections, transactions);
        var findingService = new FindingService(findingRepository, writeGuard);
        var reportService = new ReportService(studyService, forms, responses, quality, versions, findingService, audits, analysisService);
        return new Fixture(study.id(), studyTitle, focus, analysisService, findingService, reportService, audits);
    }

    private record Fixture(UUID studyId, String studyTitle, Question focus, AnalysisService analysisService,
                           FindingService findingService, ReportService reportService, AuditService auditService) { }
}
