package researchflow.analysis;

import researchflow.domain.Answer;

final class AnalysisSupport {
    private AnalysisSupport() { }

    static String displayValue(Answer answer) {
        if (answer instanceof Answer.Text value) return value.value();
        if (answer instanceof Answer.Number value) return trimNumber(value.value());
        if (answer instanceof Answer.BooleanValue value) return value.value() ? "Yes" : "No";
        if (answer instanceof Answer.DateValue value) return value.value().toString();
        var choice = (Answer.Choice) answer;
        return String.join(", ", choice.values());
    }

    private static String trimNumber(double value) {
        return value == Math.floor(value) && !Double.isInfinite(value) ? Long.toString((long) value) : Double.toString(value);
    }
}

