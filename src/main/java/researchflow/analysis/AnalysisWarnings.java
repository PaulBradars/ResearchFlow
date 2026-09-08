package researchflow.analysis;

import researchflow.domain.AnalysisMethod;

import java.util.ArrayList;
import java.util.List;

public final class AnalysisWarnings {
    private static final int SMALL_SAMPLE_THRESHOLD = 10;
    private static final double HEAVY_MISSINGNESS_THRESHOLD = 0.2;

    private AnalysisWarnings() { }

    public static List<String> compute(AnalysisMethod method, int sampleSize, int eligibleResponses) {
        var warnings = new ArrayList<String>();
        if (sampleSize < SMALL_SAMPLE_THRESHOLD) {
            warnings.add("Sample size is small (n=" + sampleSize + "); interpret results with caution.");
        }
        if (eligibleResponses > 0 && sampleSize < eligibleResponses) {
            var missingFraction = 1 - (sampleSize / (double) eligibleResponses);
            if (missingFraction > HEAVY_MISSINGNESS_THRESHOLD) {
                warnings.add(Math.round(missingFraction * 100)
                        + "% of eligible responses are missing a value needed for this analysis.");
            }
        }
        if (method == AnalysisMethod.CORRELATION) warnings.add("Correlation does not imply causation.");
        if (method == AnalysisMethod.GROUP_COMPARISON) {
            warnings.add("This comparison is observational; it does not establish a causal effect.");
        }
        return List.copyOf(warnings);
    }
}

