package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.domain.AnalysisFilter;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.AnalysisResult;
import researchflow.domain.DatasetFilterOperator;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;
import researchflow.service.AnalysisService;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcAnalysisRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void runsEachStrategyAgainstAResolvedVersionAndPersistsEvidence() throws Exception {
        var fixture = fixture();

        var numeric = fixture.analysis().run(fixture.studyId(),
                new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, fixture.sleep().id(), null, List.of(), null));
        assertEquals(AnalysisMethod.NUMERIC_SUMMARY, numeric.method());
        assertEquals(4, numeric.sampleSize());
        var summary = (AnalysisResult.NumericSummary) numeric.result();
        assertEquals(6.5, summary.mean(), 1e-9); // (5+6+7+8)/4

        // No version existed yet, so run() must have auto-created a baseline snapshot bound to this analysis.
        assertNotNull(numeric.datasetVersionId());
        assertEquals(1, fixture.versions().list(fixture.studyId()).size());

        var frequency = fixture.analysis().run(fixture.studyId(),
                new AnalysisPlan(AnalysisMethod.FREQUENCY, fixture.studyTime().id(), null, List.of(), null));
        var frequencyResult = (AnalysisResult.Frequency) frequency.result();
        assertEquals(4, frequencyResult.totalCount());

        var correlation = fixture.analysis().run(fixture.studyId(),
                new AnalysisPlan(AnalysisMethod.CORRELATION, fixture.sleep().id(), fixture.focus().id(), List.of(), null));
        assertEquals(4, ((AnalysisResult.Correlation) correlation.result()).count());

        var history = fixture.analysis().history(fixture.studyId());
        assertEquals(3, history.size());
        var stored = fixture.analysis().details(history.getFirst().id());
        assertEquals(fixture.studyId(), stored.studyId());
        assertTrue(stored.resultJson().contains("\"type\""));
    }

    @Test
    void reRunningTheSamePlanAgainstAnExplicitVersionReproducesTheResultEvenAfterLiveDataChanges() {
        var fixture = fixture();
        var version = fixture.versions().createSnapshot(fixture.studyId(), "Baseline for reproducibility check", "");

        var plan = new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, fixture.sleep().id(), null, List.of(), version.id());
        var first = fixture.analysis().run(fixture.studyId(), plan);

        // Correct a live answer after the version was created; the bound analysis must not see it.
        fixture.corrections().correct(fixture.studyId(), fixture.responseIds().getFirst(), fixture.sleep().id(),
                "1", "Simulated later correction");

        var second = fixture.analysis().run(fixture.studyId(), plan);

        assertEquals(first.result(), second.result());
        assertEquals(4, ((AnalysisResult.NumericSummary) second.result()).count());
        assertEquals(6.5, ((AnalysisResult.NumericSummary) second.result()).mean(), 1e-9);
    }

    @Test
    void groupComparisonAndFilteredQueriesProduceExpectedEvidence() {
        var fixture = fixture();

        var plan = new AnalysisPlan(AnalysisMethod.GROUP_COMPARISON, fixture.sleep().id(), fixture.studyTime().id(),
                List.of(), null);
        var evidence = fixture.analysis().run(fixture.studyId(), plan);
        var comparison = (AnalysisResult.GroupComparison) evidence.result();
        assertTrue(comparison.groupACount() + comparison.groupBCount() <= 4);

        var filtered = fixture.analysis().run(fixture.studyId(), new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY,
                fixture.sleep().id(), null,
                List.of(new AnalysisFilter(fixture.sleep().id(), DatasetFilterOperator.GREATER_THAN, "6")), null));
        var summary = (AnalysisResult.NumericSummary) filtered.result();
        assertEquals(2, summary.count()); // 7 and 8 hours
    }

    private Fixture fixture() {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var writeGuard = new researchflow.service.StudyWriteGuard(new JdbcStudyRepository(connections, transactions));
        var study = new StudyService(new JdbcStudyRepository(connections, transactions))
                .create("Analysis Study", "", "", "", null, null, List.of());
        var formRepository = new JdbcFormRepository(connections, transactions);
        var forms = new FormService(formRepository, writeGuard);
        var form = forms.create(study.id(), "Survey", "");
        var sleep = Question.create("sleep_hours", "Sleep hours", "", QuestionType.NUMBER, true, 0d, 16d, List.of());
        var focus = Question.create("focus", "Focus", "", QuestionType.NUMBER, true, 0d, 10d, List.of());
        var studyTime = Question.create("study_time", "Study time", "", QuestionType.SINGLE_CHOICE, true, null, null,
                List.of(researchflow.domain.QuestionOption.create("Morning"), researchflow.domain.QuestionOption.create("Evening")));
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(sleep, focus, studyTime))));
        form = forms.activate(form.id());
        var responseRepository = new JdbcResponseRepository(connections, transactions);
        var submissions = new ResponseSubmissionService(formRepository, responseRepository, writeGuard);
        var rows = List.of(new String[]{"5", "2", "Evening"}, new String[]{"6", "3", "Evening"},
                new String[]{"7", "4", "Morning"}, new String[]{"8", "5", "Morning"});
        var responseIds = new java.util.ArrayList<UUID>();
        for (var row : rows) {
            var response = submissions.submit(form.id(), Instant.now().minusSeconds(60),
                    Map.of(sleep.id(), row[0], focus.id(), row[1], studyTime.id(), row[2]));
            responseIds.add(response.id());
        }
        var versionRepository = new JdbcVersionRepository(connections, transactions);
        var versions = new VersionService(versionRepository, writeGuard);
        var analysisRepository = new JdbcAnalysisRepository(connections, transactions);
        var analysis = new AnalysisService(formRepository, versionRepository, analysisRepository, writeGuard);
        var datasetRepository = new JdbcDatasetRepository(connections, transactions);
        var corrections = new DatasetCorrectionService(datasetRepository, formRepository, writeGuard);
        return new Fixture(study.id(), sleep, focus, studyTime, versions, analysis, corrections, responseIds);
    }

    private record Fixture(UUID studyId, Question sleep, Question focus, Question studyTime, VersionService versions,
                           AnalysisService analysis, DatasetCorrectionService corrections, List<UUID> responseIds) { }
}
