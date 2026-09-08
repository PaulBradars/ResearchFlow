package researchflow.analysis;

import org.junit.jupiter.api.Test;
import researchflow.domain.Answer;
import researchflow.domain.AnalysisFilter;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.AnalysisResult;
import researchflow.domain.DatasetFilterOperator;
import researchflow.service.ValidationException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisStrategiesTest {
    private static final double DELTA = 1e-9;

    @Test
    void frequencyCountsEachSelectedValueAndTracksMissing() {
        var variable = UUID.randomUUID();
        var responses = List.of(
                singleAnswer(variable, choice(variable, "Morning")),
                singleAnswer(variable, choice(variable, "Morning")),
                singleAnswer(variable, choice(variable, "Evening")),
                Map.<UUID, Answer>of());

        var result = (AnalysisResult.Frequency) new FrequencyStrategy().execute(context(
                new AnalysisPlan(AnalysisMethod.FREQUENCY, variable, null, List.of(), null), responses));

        assertEquals(3, result.totalCount());
        assertEquals(1, result.missingCount());
        assertEquals(2, result.categories().size());
        var morning = result.categories().stream().filter(c -> c.value().equals("Morning")).findFirst().orElseThrow();
        assertEquals(2, morning.count());
        assertEquals(200.0 / 3, morning.percentage(), 1e-6);
    }

    @Test
    void numericSummaryMatchesHandComputedStatistics() {
        var variable = UUID.randomUUID();
        var responses = List.of(singleAnswer(variable, number(variable, 10)),
                singleAnswer(variable, number(variable, 20)), singleAnswer(variable, number(variable, 30)));

        var result = (AnalysisResult.NumericSummary) new NumericSummaryStrategy().execute(context(
                new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, variable, null, List.of(), null), responses));

        assertEquals(3, result.count());
        assertEquals(0, result.missingCount());
        assertEquals(20.0, result.mean(), DELTA);
        assertEquals(20.0, result.median(), DELTA);
        assertEquals(10.0, result.standardDeviation(), DELTA);
        assertEquals(10.0, result.minimum(), DELTA);
        assertEquals(30.0, result.maximum(), DELTA);
    }

    @Test
    void correlationIsPerfectForAPerfectLinearPairing() {
        var x = UUID.randomUUID();
        var y = UUID.randomUUID();
        var responses = List.of(paired(x, 1, y, 2), paired(x, 2, y, 4), paired(x, 3, y, 6));

        var result = (AnalysisResult.Correlation) new CorrelationStrategy().execute(context(
                new AnalysisPlan(AnalysisMethod.CORRELATION, x, y, List.of(), null), responses));

        assertEquals(3, result.count());
        assertEquals(1.0, result.coefficient(), 1e-9);
    }

    @Test
    void crossTabulationBuildsAContingencyTableFromPairwiseCompleteResponses() {
        var row = UUID.randomUUID();
        var column = UUID.randomUUID();
        var responses = List.of(
                pairedChoice(row, "Morning", column, "Yes"),
                pairedChoice(row, "Morning", column, "No"),
                pairedChoice(row, "Evening", column, "Yes"));

        var result = (AnalysisResult.CrossTabulation) new CrossTabulationStrategy().execute(context(
                new AnalysisPlan(AnalysisMethod.CROSS_TABULATION, row, column, List.of(), null), responses));

        assertEquals(List.of("Evening", "Morning"), result.rowLabels());
        assertEquals(List.of("No", "Yes"), result.columnLabels());
        assertEquals(3, result.totalCount());
        assertEquals(1, result.counts().get(0).get(1));
        assertEquals(1, result.counts().get(1).get(0));
        assertEquals(1, result.counts().get(1).get(1));
    }

    @Test
    void groupComparisonMatchesStatisticsAndRejectsMoreThanTwoGroups() {
        var outcome = UUID.randomUUID();
        var group = UUID.randomUUID();
        var twoGroupResponses = List.of(
                pairedChoice(outcome, group, 30, "A"), pairedChoice(outcome, group, 32, "A"),
                pairedChoice(outcome, group, 34, "A"), pairedChoice(outcome, group, 36, "A"),
                pairedChoice(outcome, group, 38, "A"),
                pairedChoice(outcome, group, 20, "B"), pairedChoice(outcome, group, 22, "B"),
                pairedChoice(outcome, group, 24, "B"), pairedChoice(outcome, group, 26, "B"),
                pairedChoice(outcome, group, 28, "B"));

        var result = (AnalysisResult.GroupComparison) new GroupComparisonStrategy().execute(context(
                new AnalysisPlan(AnalysisMethod.GROUP_COMPARISON, outcome, group, List.of(), null), twoGroupResponses));

        assertEquals(5.0, result.tStatistic(), 1e-6);
        assertEquals(10.0, result.meanDifference(), 1e-9);

        var threeGroupResponses = List.of(pairedChoice(outcome, group, 1, "A"), pairedChoice(outcome, group, 2, "B"),
                pairedChoice(outcome, group, 3, "C"));
        var context = context(new AnalysisPlan(AnalysisMethod.GROUP_COMPARISON, outcome, group, List.of(), null),
                threeGroupResponses);
        assertThrows(ValidationException.class, () -> new GroupComparisonStrategy().execute(context));
    }

    @Test
    void analysisDataFiltersOutExcludedResponsesAndAppliesStructuredFilters() {
        var variable = UUID.randomUUID();
        var raw = List.of(
                new researchflow.domain.VersionAnswer(UUID.randomUUID(), variable, number(variable, 5), false),
                new researchflow.domain.VersionAnswer(UUID.randomUUID(), variable, number(variable, 50), false),
                new researchflow.domain.VersionAnswer(UUID.randomUUID(), variable, number(variable, 999), true));

        var grouped = AnalysisData.group(raw);
        assertEquals(2, grouped.size());

        var filtered = AnalysisData.applyFilters(grouped,
                List.of(new AnalysisFilter(variable, DatasetFilterOperator.GREATER_THAN, "10")), Map.of());
        assertEquals(1, filtered.size());
        assertTrue(filtered.getFirst().get(variable) instanceof Answer.Number number && number.value() == 50);
    }

    private static AnalysisContext context(AnalysisPlan plan, List<Map<UUID, Answer>> responses) {
        return new AnalysisContext(plan, Map.of(), responses);
    }

    private static Map<UUID, Answer> singleAnswer(UUID questionId, Answer answer) {
        var map = new LinkedHashMap<UUID, Answer>();
        map.put(questionId, answer);
        return map;
    }

    private static Map<UUID, Answer> paired(UUID questionX, double x, UUID questionY, double y) {
        var map = new LinkedHashMap<UUID, Answer>();
        map.put(questionX, number(questionX, x));
        map.put(questionY, number(questionY, y));
        return map;
    }

    private static Map<UUID, Answer> pairedChoice(UUID questionRow, String rowValue, UUID questionColumn, String columnValue) {
        var map = new LinkedHashMap<UUID, Answer>();
        map.put(questionRow, choice(questionRow, rowValue));
        map.put(questionColumn, choice(questionColumn, columnValue));
        return map;
    }

    private static Map<UUID, Answer> pairedChoice(UUID outcomeQuestion, UUID groupQuestion, double outcomeValue, String groupValue) {
        var map = new LinkedHashMap<UUID, Answer>();
        map.put(outcomeQuestion, number(outcomeQuestion, outcomeValue));
        map.put(groupQuestion, choice(groupQuestion, groupValue));
        return map;
    }

    private static Answer.Number number(UUID questionId, double value) {
        return new Answer.Number(UUID.randomUUID(), questionId, value, Instant.now());
    }

    private static Answer.Choice choice(UUID questionId, String value) {
        return new Answer.Choice(UUID.randomUUID(), questionId, List.of(value), Instant.now());
    }
}

