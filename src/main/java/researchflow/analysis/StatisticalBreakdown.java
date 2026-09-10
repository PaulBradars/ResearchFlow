package researchflow.analysis;

import researchflow.domain.AnalysisResult;
import researchflow.domain.EvidenceBundle;

import java.util.List;
import java.util.Locale;

/** Reproducible chat report. Supplemental numeric summaries use the same filtered snapshot and paired rows. */
public final class StatisticalBreakdown {
    private StatisticalBreakdown() { }

    public static String render(EvidenceBundle evidence, List<Double> primary, List<Double> secondary) {
        var out = new StringBuilder("Statistical breakdown\n");
        out.append("Method: ").append(evidence.method()).append('\n');
        out.append("Variables: ").append(String.join(", ", evidence.variables().stream()
                .map(EvidenceBundle.VariableRef::label).toList())).append('\n');
        out.append("Analyzed sample: ").append(evidence.sampleSize()).append('\n');
        out.append("Filters: ").append(evidence.filters().isEmpty() ? "None" : evidence.filters()).append('\n');
        out.append("Dataset snapshot: ").append(evidence.datasetVersionId()).append("\n\n");
        switch (evidence.result()) {
            case AnalysisResult.Correlation r -> {
                out.append("Paired observations: ").append(r.count()).append('\n');
                out.append("Only rows with numeric values for both variables are included.\n");
                boolean valid = r.count() >= 2 && primary.size() == r.count() && secondary.size() == r.count()
                        && primary.stream().distinct().limit(2).count() == 2
                        && secondary.stream().distinct().limit(2).count() == 2;
                if (valid) {
                    out.append("Pearson r: ").append(number(r.coefficient())).append('\n');
                    out.append("Direction: ").append(r.coefficient() > 0 ? "positive" : r.coefficient() < 0 ? "negative" : "zero").append('\n');
                    out.append("r squared: ").append(number(r.coefficient() * r.coefficient()))
                            .append(" (simple linear fit with an intercept; ")
                            .append(number(100 * r.coefficient() * r.coefficient())).append("% of outcome variation accounted for in this sample).\n");
                    out.append("An r near zero indicates little linear association; a nonlinear relationship may still exist.\n");
                } else {
                    out.append("Pearson r and r squared: not estimable with fewer than two pairs or a constant variable. "
                            + "Do not interpret a stored zero placeholder as no relationship.\n");
                }
                out.append("\nDescriptive statistics on the same paired rows\n");
                describe(out, evidence.variables().get(0).label(), primary);
                describe(out, evidence.variables().get(1).label(), secondary);
                out.append("\nInterpretation limits\nCorrelation does not imply causation. Check a scatter plot for "
                        + "outliers, nonlinear patterns, and clusters; these have not been checked automatically. "
                        + "No p-value or confidence interval was computed; statistical significance is not established.\n");
                for (var variable : evidence.variables()) {
                    if (identifier(variable.label())) out.append("Variable caution: ").append(variable.label())
                            .append(" looks like an identifier. If it is an arbitrary record key, its numeric ordering "
                                    + "has no substantive measurement meaning; a correlation may reflect record ordering. "
                                    + "Confirm its meaning before drawing a research conclusion.\n");
                }
            }
            case AnalysisResult.NumericSummary r -> {
                out.append("Valid numeric values: ").append(r.count()).append("; missing: ").append(r.missingCount()).append('\n');
                if (r.count() > 0) {
                    out.append("Mean: ").append(number(r.mean())).append("; median: ").append(number(r.median())).append('\n');
                    out.append("Sample standard deviation: ").append(r.count() < 2 ? "not estimable" : number(r.standardDeviation())).append('\n');
                    out.append("Minimum: ").append(number(r.minimum())).append("; maximum: ").append(number(r.maximum()))
                            .append("; range: ").append(number(r.maximum() - r.minimum())).append('\n');
                } else out.append("No valid numeric observations; summary statistics are not estimable.\n");
                out.append("The mean describes average level; the median describes the midpoint. Standard deviation "
                        + "describes spread in the variable's units. Differences between mean and median can warrant checking the distribution.\n");
            }
            case AnalysisResult.Frequency r -> {
                out.append("Valid responses: ").append(r.totalCount()).append("; missing: ").append(r.missingCount()).append('\n');
                out.append("Category — count — percentage of valid responses\n");
                for (var category : r.categories()) out.append(category.value()).append(" — ").append(category.count())
                        .append(" — ").append(number(category.percentage())).append("%\n");
                out.append("Missing responses are reported separately. Multiple-choice percentages can sum to more than 100%. "
                        + "Percentages describe this sample, not necessarily the population.\n");
            }
            case AnalysisResult.CrossTabulation r -> {
                out.append("Complete categorical pairs: ").append(r.totalCount()).append('\n');
                out.append("Each cell shows count, row percentage, and percentage of the complete-pair total.\n");
                for (int row = 0; row < r.rowLabels().size(); row++) {
                    int total = r.counts().get(row).stream().mapToInt(Integer::intValue).sum();
                    out.append(r.rowLabels().get(row)).append(" (row total ").append(total).append(")\n");
                    for (int col = 0; col < r.columnLabels().size(); col++) {
                        int count = r.counts().get(row).get(col);
                        out.append("  ").append(r.columnLabels().get(col)).append(": ").append(count)
                                .append("; row ").append(percent(count, total)).append("; total ")
                                .append(percent(count, r.totalCount())).append('\n');
                    }
                }
                out.append("These are descriptive differences. No chi-square test or p-value was computed.\n");
            }
            case AnalysisResult.GroupComparison r -> {
                out.append(r.groupALabel()).append(": n=").append(r.groupACount()).append("; mean=")
                        .append(number(r.groupAMean())).append("; sample SD=").append(number(r.groupASD())).append('\n');
                out.append(r.groupBLabel()).append(": n=").append(r.groupBCount()).append("; mean=")
                        .append(number(r.groupBMean())).append("; sample SD=").append(number(r.groupBSD())).append('\n');
                out.append("Mean difference (first group minus second): ").append(number(r.meanDifference())).append('\n');
                boolean estimable = r.groupACount() >= 2 && r.groupBCount() >= 2
                        && (r.groupASD() > 0 || r.groupBSD() > 0);
                out.append("Welch t: ").append(estimable ? number(r.tStatistic()) : "not estimable").append("; degrees of freedom: ")
                        .append(estimable ? number(r.degreesOfFreedom()) : "not estimable").append('\n');
                out.append("Cohen's d (pooled SD): ").append(estimable ? number(r.cohensD()) : "not estimable").append('\n');
                out.append("The mean difference is in the original units; Cohen's d expresses the difference in standard-deviation units. "
                        + "Check independent observations, group sizes, outliers, and distributions. "
                        + "No p-value or confidence interval was computed; do not conclude statistical significance. "
                        + "Zero-variance groups can make standardized statistics undefined.\n");
            }
        }
        if (!evidence.warnings().isEmpty()) out.append("\nData quality and limitations\n- ")
                .append(String.join("\n- ", evidence.warnings())).append('\n');
        return out.toString().strip();
    }

    private static void describe(StringBuilder out, String label, List<Double> values) {
        out.append(label).append(": ");
        if (values.isEmpty()) { out.append("no valid values\n"); return; }
        double mean = Statistics.mean(values);
        out.append("n=").append(values.size()).append("; mean=").append(number(mean))
                .append("; median=").append(number(Statistics.median(values)))
                .append("; sample SD=").append(values.size() < 2 ? "not estimable" : number(Statistics.standardDeviation(values, mean)))
                .append("; min=").append(number(values.stream().mapToDouble(Double::doubleValue).min().orElseThrow()))
                .append("; max=").append(number(values.stream().mapToDouble(Double::doubleValue).max().orElseThrow())).append('\n');
    }

    private static String percent(int count, int total) {
        return total == 0 ? "not estimable" : number(100.0 * count / total) + "%";
    }

    private static boolean identifier(String label) {
        return label.matches("(?i).*(?:\\b|_)id(?:\\b|_).*") || label.endsWith("ID") || label.endsWith("Id")
                || label.equalsIgnoreCase("customerid");
    }

    public static String number(double value) {
        if (!Double.isFinite(value)) return "not estimable";
        if (value != 0 && Math.abs(value) < 0.0001) return String.format(Locale.ROOT, "%.4e", value);
        return String.format(Locale.ROOT, "%.4f", value);
    }
}
