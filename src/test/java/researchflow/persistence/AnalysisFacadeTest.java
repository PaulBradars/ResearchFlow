package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.ai.FakeLlmClient;
import researchflow.ai.LlmException;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.ChatRole;
import researchflow.domain.Question;
import researchflow.domain.QuestionOption;
import researchflow.domain.QuestionType;
import researchflow.service.AnalysisFacade;
import researchflow.service.AnalysisService;
import researchflow.service.FormService;
import researchflow.service.ResponseSubmissionService;
import researchflow.service.StudyService;
import researchflow.service.ValidationException;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * "Ask Your Data" exit-gate checks: at least five representative natural-language questions map
 * to valid plans across every method; malformed/unsupported/hallucinated plans never execute;
 * manual analysis keeps working with no AI; and an explanation failure never loses already-computed
 * evidence. No real LLM is called — {@link FakeLlmClient} stands in for the local runtime.
 */
class AnalysisFacadeTest {
    @TempDir Path temporaryDirectory;

    @Test
    void greetingWorksOfflineAndNeverCreatesStatisticalEvidence() {
        var fixture = fixture(FakeLlmClient.unavailable());
        var answer = fixture.facade().ask(fixture.studyId(), " How are you? ");
        org.junit.jupiter.api.Assertions.assertNull(answer.evidence());
        assertTrue(answer.explanation().contains("ready to help"));
        assertEquals(2, fixture.facade().history(fixture.studyId()).size());
        assertTrue(fixture.facade().history(fixture.studyId()).stream().allMatch(m -> m.analysisId() == null));
    }

    @Test
    void generalQuestionsAndClarificationsNeedOnlyOneModelReply() {
        var fixture = fixture(FakeLlmClient.available());
        fixture.llm().thenRespond("{\"reply\":\"Correlation describes how two variables move together.\"}");
        var answer = fixture.facade().ask(fixture.studyId(), "What is correlation?");
        org.junit.jupiter.api.Assertions.assertNull(answer.evidence());
        assertTrue(answer.explanation().contains("two variables"));
        fixture.llm().thenRespond("{\"reply\":\"Which variable would you like to summarize?\"}");
        assertTrue(fixture.facade().ask(fixture.studyId(), "Summarize it").explanation().contains("Which variable"));
        fixture.llm().thenRespond("{\"reply\":\"\"}");
        assertThrows(LlmException.class, () -> fixture.facade().ask(fixture.studyId(), "help"));
        assertEquals(4, fixture.facade().history(fixture.studyId()).size());
    }

    @Test
    void exportIncludesAllMessagesAndClearPreservesAnalyses() {
        var fixture = fixture(FakeLlmClient.available());
        fixture.llm().thenRespond(planJson("NUMERIC_SUMMARY", fixture.focus().id(), null)).thenRespond("Focus summary.");
        var evidence = fixture.facade().ask(fixture.studyId(), "Average focus?").evidence();
        var connections = TestDatabase.migrated(temporaryDirectory);
        var otherStudy = new StudyService(new JdbcStudyRepository(connections, new TransactionManager(connections)))
                .create("Other", "", "", "", null, null, List.of());
        fixture.facade().ask(otherStudy.id(), "hello");
        for (int i = 0; i < 130; i++) fixture.facade().ask(fixture.studyId(), "hello");
        assertEquals(250, fixture.facade().history(fixture.studyId()).size());
        assertEquals("hello", fixture.facade().history(fixture.studyId()).getFirst().content());
        var exported = fixture.facade().exportHistory(fixture.studyId());
        assertTrue(exported.contains("Average focus?"));
        assertTrue(exported.contains(evidence.id().toString()));
        assertEquals(262, exported.lines().filter(line -> line.contains(" | ")).count());
        fixture.facade().clearHistory(fixture.studyId());
        assertTrue(fixture.facade().history(fixture.studyId()).isEmpty());
        assertEquals(2, fixture.facade().history(otherStudy.id()).size());
        assertFalse(fixture.facade().exportHistory(fixture.studyId()).contains("Average focus?"));
        assertTrue(new JdbcAnalysisRepository(connections, new TransactionManager(connections))
                .findById(evidence.id()).isPresent());
    }

    @Test
    void fiveRepresentativeQuestionsMapToValidPlansAcrossEveryMethod() {
        var fixture = fixture(FakeLlmClient.available());
        var llm = fixture.llm();

        llm.thenRespond(planJson("CORRELATION", fixture.sleep().id(), fixture.focus().id()))
                .thenRespond("Sleep and focus move together.");
        var correlation = fixture.facade().ask(fixture.studyId(), "How is sleep duration associated with academic focus?");
        assertEquals(AnalysisMethod.CORRELATION, correlation.evidence().method());
        assertTrue(correlation.explanation().contains("Pearson r: 1.0000"));
        assertTrue(correlation.explanation().contains("Sleep hours: n=4; mean=6.5000"));
        assertTrue(correlation.explanation().contains("Focus: n=4; mean=3.5000"));
        assertTrue(fixture.facade().exportHistory(fixture.studyId()).contains("Descriptive statistics on the same paired rows"));

        llm.thenRespond(planJson("NUMERIC_SUMMARY", fixture.focus().id(), null))
                .thenRespond("Average focus is moderate.");
        var summary = fixture.facade().ask(fixture.studyId(), "What is the average focus rating?");
        assertEquals(AnalysisMethod.NUMERIC_SUMMARY, summary.evidence().method());

        llm.thenRespond(planJson("FREQUENCY", fixture.studyTime().id(), null))
                .thenRespond("Most respondents prefer mornings.");
        var frequency = fixture.facade().ask(fixture.studyId(), "How many people prefer studying in the morning vs evening?");
        assertEquals(AnalysisMethod.FREQUENCY, frequency.evidence().method());

        llm.thenRespond(planJson("GROUP_COMPARISON", fixture.focus().id(), fixture.studyTime().id()))
                .thenRespond("Morning responses show higher focus.");
        var comparison = fixture.facade().ask(fixture.studyId(), "Does focus differ between morning and evening study groups?");
        assertEquals(AnalysisMethod.GROUP_COMPARISON, comparison.evidence().method());

        llm.thenRespond(planJson("CROSS_TABULATION", fixture.studyTime().id(), fixture.mood().id()))
                .thenRespond("Morning respondents report a positive mood more often.");
        var crossTab = fixture.facade().ask(fixture.studyId(), "How does study time preference relate to mood?");
        assertEquals(AnalysisMethod.CROSS_TABULATION, crossTab.evidence().method());

        var history = fixture.facade().history(fixture.studyId());
        assertEquals(10, history.size()); // 5 USER + 5 ASSISTANT turns
        assertEquals(5, history.stream().filter(message -> message.role() == ChatRole.USER).count());
        assertEquals(5, history.stream().filter(message -> message.role() == ChatRole.ASSISTANT).count());
        assertTrue(history.stream().allMatch(message -> message.analysisId() != null));
    }

    @Test
    void malformedAndHallucinatedAiResponsesNeverExecute() {
        var fixture = fixture(FakeLlmClient.available());

        fixture.llm().thenRespond("I'm sorry, I don't understand the question.");
        assertThrows(LlmException.class, () -> fixture.facade().ask(fixture.studyId(), "What is going on?"));

        fixture.llm().thenRespond(planJson("REGRESSION", fixture.sleep().id(), null));
        assertThrows(LlmException.class, () -> fixture.facade().ask(fixture.studyId(), "Run a regression."));

        // Structurally valid JSON, but CORRELATION against a non-number variable is semantically unsupported.
        // AnalysisPlanValidator (the same one manual analysis uses) rejects it before any calculation runs.
        fixture.llm().thenRespond(planJson("CORRELATION", fixture.sleep().id(), fixture.studyTime().id()));
        assertThrows(ValidationException.class,
                () -> fixture.facade().ask(fixture.studyId(), "Correlate sleep with study time preference."));
    }

    @Test
    void manualAnalysisKeepsWorkingWhenAiIsUnavailable() {
        var fixture = fixture(FakeLlmClient.unavailable());

        assertFalse(fixture.facade().aiAvailable());
        assertThrows(LlmException.class, () -> fixture.facade().ask(fixture.studyId(), "How is sleep associated with focus?"));

        var evidence = fixture.analysisService().run(fixture.studyId(),
                new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, fixture.focus().id(), null, List.of(), null));
        assertEquals(AnalysisMethod.NUMERIC_SUMMARY, evidence.method());
        assertTrue(evidence.sampleSize() > 0);
    }

    @Test
    void anExplanationFailureFallsBackGracefullyWithoutLosingTheAlreadyComputedEvidence() {
        var fixture = fixture(FakeLlmClient.available());
        fixture.llm().thenRespond(planJson("NUMERIC_SUMMARY", fixture.focus().id(), null))
                .thenFail(LlmException.Kind.TIMEOUT, "timed out");

        var answer = fixture.facade().ask(fixture.studyId(), "What is the average focus rating?");

        assertNotNull(answer.evidence());
        assertEquals(AnalysisMethod.NUMERIC_SUMMARY, answer.evidence().method());
        assertTrue(answer.explanation().contains("could not be generated"));
        assertTrue(answer.explanation().contains("Statistical breakdown"));
        assertTrue(answer.explanation().contains("Mean: 3.5000"));
        assertEquals(2, fixture.facade().history(fixture.studyId()).size());
    }

    private static String planJson(String method, UUID primary, UUID secondary) {
        return "{\"method\":\"" + method + "\",\"primaryVariableId\":\"" + primary + "\",\"secondaryVariableId\":"
                + (secondary == null ? "null" : "\"" + secondary + "\"") + ",\"filters\":[]}";
    }

    private Fixture fixture(FakeLlmClient llm) {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var writeGuard = new researchflow.service.StudyWriteGuard(new JdbcStudyRepository(connections, transactions));
        var study = new StudyService(new JdbcStudyRepository(connections, transactions))
                .create("AI Study", "", "", "", null, null, List.of());
        var formRepository = new JdbcFormRepository(connections, transactions);
        var forms = new FormService(formRepository, writeGuard);
        var form = forms.create(study.id(), "Survey", "");
        var sleep = Question.create("sleep_hours", "Sleep hours", "", QuestionType.NUMBER, true, 0d, 16d, List.of());
        var focus = Question.create("focus", "Focus", "", QuestionType.NUMBER, true, 0d, 10d, List.of());
        var studyTime = Question.create("study_time", "Study time", "", QuestionType.SINGLE_CHOICE, true, null, null,
                List.of(QuestionOption.create("Morning"), QuestionOption.create("Evening")));
        var mood = Question.create("mood", "Positive mood", "", QuestionType.YES_NO, true, null, null, List.of());
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(sleep, focus, studyTime, mood))));
        form = forms.activate(form.id());
        var responseRepository = new JdbcResponseRepository(connections, transactions);
        var submissions = new ResponseSubmissionService(formRepository, responseRepository, writeGuard);
        var rows = List.of(new String[]{"5", "2", "Evening", "No"}, new String[]{"6", "3", "Evening", "No"},
                new String[]{"7", "4", "Morning", "Yes"}, new String[]{"8", "5", "Morning", "Yes"});
        for (var row : rows) {
            submissions.submit(form.id(), Instant.now().minusSeconds(60),
                    Map.of(sleep.id(), row[0], focus.id(), row[1], studyTime.id(), row[2], mood.id(), row[3]));
        }
        var versionRepository = new JdbcVersionRepository(connections, transactions);
        var analysisRepository = new JdbcAnalysisRepository(connections, transactions);
        var analysisService = new AnalysisService(formRepository, versionRepository, analysisRepository, writeGuard);
        var chatRepository = new JdbcChatRepository(connections);
        var facade = new AnalysisFacade(llm, formRepository, analysisService, chatRepository);
        return new Fixture(study.id(), sleep, focus, studyTime, mood, analysisService, facade, llm);
    }

    private record Fixture(UUID studyId, Question sleep, Question focus, Question studyTime, Question mood,
                           AnalysisService analysisService, AnalysisFacade facade, FakeLlmClient llm) { }
}
