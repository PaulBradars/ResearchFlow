package researchflow.analysis;

import researchflow.domain.AnalysisResult;

import java.util.List;

/**
 * Deterministic, hand-verifiable arithmetic shared by the strategies. Mean/median/sample standard
 * deviation and Pearson correlation use their textbook formulas directly. The two-group comparison
 * reports Welch's t-statistic, its Welch–Satterthwaite degrees of freedom, and Cohen's d effect
 * size — all computable without a special-function (incomplete beta) library, so no external
 * statistics dependency is required. A p-value is intentionally not reported.
 */
public final class Statistics {
    private Statistics() { }

    public static double mean(List<Double> values) {
        return values.isEmpty() ? 0 : values.stream().mapToDouble(Double::doubleValue).sum() / values.size();
    }

    public static double median(List<Double> values) {
        if (values.isEmpty()) return 0;
        var sorted = values.stream().sorted().toList();
        var size = sorted.size();
        return size % 2 == 1 ? sorted.get(size / 2) : (sorted.get(size / 2 - 1) + sorted.get(size / 2)) / 2.0;
    }

    /** Sample standard deviation (n-1 denominator); 0 when fewer than two values are given. */
    public static double standardDeviation(List<Double> values, double mean) {
        if (values.size() < 2) return 0;
        var sumSquares = values.stream().mapToDouble(value -> (value - mean) * (value - mean)).sum();
        return Math.sqrt(sumSquares / (values.size() - 1));
    }

    public static double pearson(List<Double> xs, List<Double> ys) {
        var meanX = mean(xs);
        var meanY = mean(ys);
        double numerator = 0;
        double sumX2 = 0;
        double sumY2 = 0;
        for (int index = 0; index < xs.size(); index++) {
            var dx = xs.get(index) - meanX;
            var dy = ys.get(index) - meanY;
            numerator += dx * dy;
            sumX2 += dx * dx;
            sumY2 += dy * dy;
        }
        var denominator = Math.sqrt(sumX2 * sumY2);
        return denominator == 0 ? 0 : numerator / denominator;
    }

    public static AnalysisResult.GroupComparison compareGroups(String labelA, List<Double> a, String labelB, List<Double> b) {
        var meanA = mean(a);
        var meanB = mean(b);
        var sdA = standardDeviation(a, meanA);
        var sdB = standardDeviation(b, meanB);
        var countA = a.size();
        var countB = b.size();
        var standardErrorA2 = (sdA * sdA) / countA;
        var standardErrorB2 = (sdB * sdB) / countB;
        var denominator = Math.sqrt(standardErrorA2 + standardErrorB2);
        var t = denominator == 0 ? 0 : (meanA - meanB) / denominator;
        var degreesOfFreedom = (standardErrorA2 + standardErrorB2 == 0) ? (countA + countB - 2)
                : Math.pow(standardErrorA2 + standardErrorB2, 2)
                        / (Math.pow(standardErrorA2, 2) / (countA - 1) + Math.pow(standardErrorB2, 2) / (countB - 1));
        var pooledVariance = ((countA - 1) * sdA * sdA + (countB - 1) * sdB * sdB) / (double) (countA + countB - 2);
        var pooledStandardDeviation = Math.sqrt(pooledVariance);
        var cohensD = pooledStandardDeviation == 0 ? 0 : (meanA - meanB) / pooledStandardDeviation;
        return new AnalysisResult.GroupComparison(labelA, countA, meanA, sdA, labelB, countB, meanB, sdB,
                meanA - meanB, t, degreesOfFreedom, cohensD);
    }
}
