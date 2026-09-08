package researchflow.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatisticsTest {
    private static final double DELTA = 1e-9;

    @Test
    void meanMedianAndSampleStandardDeviationMatchTheClassicTextbookExample() {
        var values = List.of(2d, 4d, 4d, 4d, 5d, 5d, 7d, 9d);

        assertEquals(5.0, Statistics.mean(values), DELTA);
        assertEquals(4.5, Statistics.median(values), DELTA);
        assertEquals(Math.sqrt(32.0 / 7.0), Statistics.standardDeviation(values, Statistics.mean(values)), DELTA);
    }

    @Test
    void standardDeviationIsZeroForFewerThanTwoValues() {
        assertEquals(0, Statistics.standardDeviation(List.of(), 0), DELTA);
        assertEquals(0, Statistics.standardDeviation(List.of(5d), 5), DELTA);
    }

    @Test
    void pearsonIsPlusOneForAPerfectLinearRelationshipAndMinusOneForAPerfectInverseOne() {
        var xs = List.of(1d, 2d, 3d, 4d, 5d);
        var perfectPositive = List.of(2d, 4d, 6d, 8d, 10d);
        var perfectNegative = List.of(10d, 8d, 6d, 4d, 2d);

        assertEquals(1.0, Statistics.pearson(xs, perfectPositive), DELTA);
        assertEquals(-1.0, Statistics.pearson(xs, perfectNegative), DELTA);
    }

    @Test
    void compareGroupsMatchesHandComputedWelchStatisticsForEqualVarianceGroups() {
        var groupA = List.of(30d, 32d, 34d, 36d, 38d);
        var groupB = List.of(20d, 22d, 24d, 26d, 28d);

        var result = Statistics.compareGroups("A", groupA, "B", groupB);

        assertEquals(34.0, result.groupAMean(), DELTA);
        assertEquals(24.0, result.groupBMean(), DELTA);
        assertEquals(10.0, result.meanDifference(), DELTA);
        assertEquals(Math.sqrt(10.0), result.groupASD(), 1e-6);
        assertEquals(5.0, result.tStatistic(), 1e-6);
        assertEquals(8.0, result.degreesOfFreedom(), 1e-6);
        assertEquals(Math.sqrt(10.0), result.cohensD(), 1e-6);
    }
}

