package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.ai.FakeLlmClient;
import researchflow.ai.LlmException;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.AnalysisResult;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;
import researchflow.service.AnalysisFacade;
import researchflow.service.AnalysisService;
import researchflow.service.FormService;
import researchflow.service.StudyService;
import researchflow.service.VersionService;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisFailurePathsTest {
    @TempDir Path temporaryDirectory;

    @Test
    void numericSummaryOnAnEmptyDatasetVersionReturnsAZeroSampleResultInsteadOfCrashing() {
        var fixture = fixture();
        var version = fixture.versions().createSnapshot(fixture.studyId(), "Empty baseline", "");

        var plan = new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, fixture.sleep().id(), null, List.of(), version.id());
        var evidence = fixture.analysis().run(fixture.studyId(), plan);

        assertEquals(0, evidence.sampleSize());
        var summary = (AnalysisResult.NumericSummary) evidence.result();
        assertEquals(0, summary.count());
        assertTrue(evidence.warnings().stream().anyMatch(warning -> warning.contains("n=0")));
    }

    @Test
    void frequencyOnAnEmptyDatasetVersionReturnsEmptyCategoriesInsteadOfCrashing() {
        var fixture = fixture();
        var version = fixture.versions().createSnapshot(fixture.studyId(), "Empty baseline", "");

        var plan = new AnalysisPlan(AnalysisMethod.FREQUENCY, fixture.studyTime().id(), null, List.of(), version.id());
        var evidence = fixture.analysis().run(fixture.studyId(), plan);

        var frequency = (AnalysisResult.Frequency) evidence.result();
        assertEquals(0, frequency.totalCount());
        assertTrue(frequency.categories().isEmpty());
    }

    @Test
    void aiPlanGenerationTimeoutIsReportedAsALlmExceptionWithoutPersistingAnyEvidence() {
        var fixture = fixture();
        var llm = FakeLlmClient.available();
        llm.thenFail(LlmException.Kind.TIMEOUT, "the local model took too long to respond");
        var facade = new AnalysisFacade(llm, fixture.formRepository(), fixture.analysis(), fixture.chatRepository());

        var failure = org.junit.jupiter.api.Assertions.assertThrows(LlmException.class,
                () -> facade.ask(fixture.studyId(), "How is sleep related to focus?"));

        assertEquals(LlmException.Kind.TIMEOUT, failure.kind());
        assertTrue(fixture.analysis().history(fixture.studyId()).isEmpty());
    }

    private Fixture fixture() {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var study = new StudyService(new JdbcStudyRepository(connections, transactions))
                .create("Empty Data Study", "", "", "", null, null, List.of());
        var formRepository = new JdbcFormRepository(connections, transactions);
        var forms = new FormService(formRepository);
        var form = forms.create(study.id(), "Survey", "");
        var sleep = Question.create("sleep_hours", "Sleep hours", "", QuestionType.NUMBER, true, 0d, 16d, List.of());
        var studyTime = Question.create("study_time", "Study time", "", QuestionType.SINGLE_CHOICE, true, null, null,
                List.of(researchflow.domain.QuestionOption.create("Morning"), researchflow.domain.QuestionOption.create("Evening")));
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(sleep, studyTime))));
        forms.activate(form.id());
        var versionRepository = new JdbcVersionRepository(connections, transactions);
        var versions = new VersionService(versionRepository);
        var analysisRepository = new JdbcAnalysisRepository(connections, transactions);
        var analysis = new AnalysisService(formRepository, versionRepository, analysisRepository);
        var chatRepository = new JdbcChatRepository(connections);
        return new Fixture(study.id(), sleep, studyTime, versions, analysis, formRepository, chatRepository);
    }

    private record Fixture(UUID studyId, Question sleep, Question studyTime, VersionService versions,
                           AnalysisService analysis, researchflow.persistence.FormRepository formRepository,
                           researchflow.persistence.ChatRepository chatRepository) { }
}
