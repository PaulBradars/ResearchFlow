package researchflow.quality;

import researchflow.domain.Answer;
import researchflow.domain.QualityIssue;
import researchflow.domain.QualityIssueType;
import researchflow.domain.QualitySeverity;
import researchflow.domain.QuestionType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

public final class OutlierHandler extends QualityHandler {
    private static final int MINIMUM_SAMPLE = 4;

    @Override
    protected List<QualityIssue> evaluate(QualityScanContext context) {
        var issues = new ArrayList<QualityIssue>();
        var byQuestion = new LinkedHashMap<UUID, List<ResponseValue>>();
        for (var response : context.responses()) {
            for (var answer : response.answers()) {
                if (answer instanceof Answer.Number number
                        && context.question(answer.questionId()).map(question -> question.type() == QuestionType.NUMBER).orElse(false)) {
                    byQuestion.computeIfAbsent(answer.questionId(), id -> new ArrayList<>())
                            .add(new ResponseValue(response.id(), number.value()));
                }
            }
        }
        for (var entry : byQuestion.entrySet()) {
            var values = entry.getValue();
            if (values.size() < MINIMUM_SAMPLE) continue;
            var fences = fences(values.stream().map(ResponseValue::value).sorted().toList());
            var question = context.question(entry.getKey()).orElseThrow();
            for (var value : values) {
                if (value.value() < fences[0] || value.value() > fences[1]) {
                    issues.add(QualityIssue.open(context.studyId(), null, QualityIssueType.OUTLIER,
                            QualitySeverity.WARNING, value.responseId(), question.id(),
                            "Value for \"" + question.label() + "\" is a statistical outlier for this variable "
                                    + "(outside the expected " + round(fences[0]) + "–" + round(fences[1]) + " range)."));
                }
            }
        }
        return issues;
    }

    private static double[] fences(List<Double> sorted) {
        var q1 = median(sorted.subList(0, sorted.size() / 2));
        var upperStart = sorted.size() % 2 == 0 ? sorted.size() / 2 : sorted.size() / 2 + 1;
        var q3 = median(sorted.subList(upperStart, sorted.size()));
        var iqr = q3 - q1;
        return new double[]{q1 - 1.5 * iqr, q3 + 1.5 * iqr};
    }

    private static double median(List<Double> values) {
        var size = values.size();
        return size % 2 == 1 ? values.get(size / 2) : (values.get(size / 2 - 1) + values.get(size / 2)) / 2.0;
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }

    private record ResponseValue(UUID responseId, double value) { }
}

