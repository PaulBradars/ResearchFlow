package researchflow.analysis;

import researchflow.domain.AnalysisResult;

public final class AnalysisResults {
    private AnalysisResults() { }

    public static int sampleSizeOf(AnalysisResult result) {
        return switch (result) {
            case AnalysisResult.Frequency value -> value.totalCount();
            case AnalysisResult.NumericSummary value -> value.count();
            case AnalysisResult.Correlation value -> value.count();
            case AnalysisResult.CrossTabulation value -> value.totalCount();
            case AnalysisResult.GroupComparison value -> value.groupACount() + value.groupBCount();
        };
    }
}

