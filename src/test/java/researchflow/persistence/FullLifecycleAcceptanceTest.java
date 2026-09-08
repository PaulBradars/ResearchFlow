package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.ai.FakeLlmClient;
import researchflow.command.ReviewCommand;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.FindingStatus;
import researchflow.domain.Question;
import researchflow.domain.QuestionOption;
import researchflow.domain.QuestionType;
import researchflow.domain.QualityIssueType;
import researchflow.service.AnalysisFacade;
import researchflow.service.AnalysisService;
import researchflow.service.AuditService;
import researchflow.service.DatasetCorrectionService;
import researchflow.service.FindingService;
import researchflow.service.QualityReviewService;
import researchflow.service.QualityService;
import researchflow.service.ReportService;
import researchflow.service.ResponseQueryService;
import researchflow.service.ResponseSubmissionService;
import researchflow.service.StudyService;
import researchflow.service.FormService;
import researchflow.service.VersionService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FullLifecycleAcceptanceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void walksTheFullResearchLifecycleFromCollectionToReport() throws Exception {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);

        var studyRepository = new JdbcStudyRepository(connections, transactions);
        var formRepository = new JdbcFormRepository(connections, transactions);
        var responseRepository = new JdbcResponseRepository(connections, transactions);
        var datasetRepository = new JdbcDatasetRepository(connections, transactions);
        var qualityRepository = new JdbcQualityRepository(connections, transactions);
        var versionRepository = new JdbcVersionRepository(connections, transactions);
        var analysisRepository = new JdbcAnalysisRepository(connections, transactions);
        var chatRepository = new JdbcChatRepository(connections);
        var findingRepository = new JdbcFindingRepository(connections, transactions);

        var studyService = new StudyService(studyRepository);
        var formService = new FormService(formRepository);
        var submissionService = new ResponseSubmissionService(formRepository, responseRepository);
        var responseQueryService = new ResponseQueryService(responseRepository);
        var correctionService = new DatasetCorrectionService(datasetRepository, formRepository);
        var qualityService = new QualityService(formRepository, responseRepository, qualityRepository);
        var qualityReviewService = new QualityReviewService(qualityRepository, correctionService);
        var versionService = new VersionService(versionRepository);
        var analysisService = new AnalysisService(formRepository, versionRepository, analysisRepository);
        var findingService = new FindingService(findingRepository);
        var auditService = new AuditService(datasetRepository);
        var reportService = new ReportService(studyService, formService, responseQueryService,
                qualityService, versionService, findingService, auditService);
        var llmClient = FakeLlmClient.available();
        var aiFacade = new AnalysisFacade(llmClient, formRepository, analysisService, chatRepository);

        var study = studyService.create("Wellbeing Acceptance Study", "Acceptance test study",
                "Check whether sleep relates to focus", "Test Researcher",
                LocalDate.now(), LocalDate.now().plusMonths(1),
                List.of("Does sleep relate to focus?"));

        var form = formService.create(study.id(), "Wellbeing Survey", "");
        var sleepQuestion = Question.create("sleep_hours", "Hours of sleep", "", QuestionType.NUMBER,
                true, 0d, 16d, List.of());
        var screenTimeQuestion = Question.create("screen_time_hours", "Screen time before bed", "", QuestionType.NUMBER,
                true, 0d, 12d, List.of());
        var studyTimeQuestion = Question.create("study_time", "Preferred study time", "", QuestionType.SINGLE_CHOICE,
                true, null, null, List.of(QuestionOption.create("Morning"), QuestionOption.create("Evening")));
        form = formService.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(sleepQuestion, screenTimeQuestion, studyTimeQuestion))));
        form = formService.activate(form.id());

        var rows = List.of(
                new String[]{"7.5", "2.0", "Morning"},
                new String[]{"6.0", "4.0", "Evening"},
                new String[]{"8.0", "1.5", "Morning"},
                new String[]{"8.0", "1.5", "Morning"},
                new String[]{"5.0", "5.0", "Evening"}
        );
        for (int index = 0; index < rows.size(); index++) {
            var row = rows.get(index);
            var answers = new LinkedHashMap<UUID, String>();
            answers.put(sleepQuestion.id(), row[0]);
            answers.put(screenTimeQuestion.id(), row[1]);
            answers.put(studyTimeQuestion.id(), row[2]);
            submissionService.submit(form.id(), Instant.now().minusSeconds(60 + index * 20L), answers);
        }

        var issues = qualityService.scan(study.id());
        var duplicateIssue = issues.stream()
                .filter(issue -> issue.type() == QualityIssueType.DUPLICATE_RESPONSE)
                .findFirst().orElseThrow();
        var resolvedIssue = qualityReviewService.apply(
                new ReviewCommand.Accept(duplicateIssue.id(), "Confirmed as an intentional duplicate for testing"));
        assertEquals(researchflow.domain.QualityIssueStatus.ACCEPTED, resolvedIssue.status());

        var version = versionService.createSnapshot(study.id(), "Snapshot after quality review", "First version");

        var summaryPlan = new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, sleepQuestion.id(), null, List.of(), version.id());
        var evidence = analysisService.run(study.id(), summaryPlan);
        assertEquals(5, evidence.sampleSize());

        var findingText = FindingService.draftText(evidence);
        var finding = findingService.draft(study.id(), evidence, findingText, null);
        finding = findingService.approve(finding.id());
        assertEquals(FindingStatus.APPROVED, finding.status());

        llmClient.thenRespond("{\"method\":\"CORRELATION\",\"primaryVariableId\":\"" + sleepQuestion.id()
                        + "\",\"secondaryVariableId\":\"" + screenTimeQuestion.id() + "\",\"filters\":[]}")
                .thenRespond("Students who sleep more tend to have less screen time before bed.");
        var aiAnswer = aiFacade.ask(study.id(), "How does screen time relate to sleep?");
        assertEquals(AnalysisMethod.CORRELATION, aiAnswer.evidence().method());

        var reportFile = temporaryDirectory.resolve("report.html");
        reportService.export(study.id(), reportFile);
        var reportContent = Files.readString(reportFile, StandardCharsets.UTF_8);
        assertTrue(reportContent.contains("Wellbeing Acceptance Study"));
        assertTrue(reportContent.contains(finding.text()));

        var timeline = auditService.timeline(study.id());
        var hasReportEvent = timeline.stream().anyMatch(event -> event.eventType().equals("REPORT_GENERATED"));
        assertTrue(hasReportEvent);
    }
}
