package researchflow.domain;

import java.util.List;

/** The typed, deterministic result of one {@link AnalysisMethod}. Never produced or altered by AI. */
public sealed interface AnalysisResult permits AnalysisResult.Frequency, AnalysisResult.NumericSummary,
        AnalysisResult.Correlation, AnalysisResult.CrossTabulation, AnalysisResult.GroupComparison {

    record Frequency(List<Category> categories, int totalCount, int missingCount) implements AnalysisResult {
        public Frequency {
            categories = List.copyOf(categories);
        }

        public record Category(String value, int count, double percentage) { }
    }

    record NumericSummary(int count, int missingCount, double mean, double median, double standardDeviation,
                          double minimum, double maximum) implements AnalysisResult { }

    record Correlation(int count, double coefficient) implements AnalysisResult { }

    record CrossTabulation(List<String> rowLabels, List<String> columnLabels, List<List<Integer>> counts,
                           int totalCount) implements AnalysisResult {
        public CrossTabulation {
            rowLabels = List.copyOf(rowLabels);
            columnLabels = List.copyOf(columnLabels);
            counts = counts.stream().map(List::copyOf).toList();
        }
    }

    record GroupComparison(String groupALabel, int groupACount, double groupAMean, double groupASD,
                           String groupBLabel, int groupBCount, double groupBMean, double groupBSD,
                           double meanDifference, double tStatistic, double degreesOfFreedom, double cohensD)
            implements AnalysisResult { }
}
