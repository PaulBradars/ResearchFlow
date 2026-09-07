package researchflow.quality;

import researchflow.domain.Answer;
import researchflow.domain.QualityIssue;
import researchflow.domain.QualityIssueType;
import researchflow.domain.QualitySeverity;

import java.util.ArrayList;
import java.util.List;

/**
 * Flags a numeric answer outside its question's configured minimum/maximum. Normal submission
 * and correction already reject out-of-range values, so this exists to catch data that entered
 * the table through another path (imports, restored versions, configuration changes).
 */
public final class InvalidRangeHandler extends QualityHandler {
    @Override
    protected List<QualityIssue> evaluate(QualityScanContext context) {
        var issues = new ArrayList<QualityIssue>();
        for (var response : context.responses()) {
            for (var answer : response.answers()) {
                if (!(answer instanceof Answer.Number number)) continue;
                var question = context.question(answer.questionId()).orElse(null);
                if (question == null) continue;
                var minimum = question.minimum();
                var maximum = question.maximum();
                if ((minimum != null && number.value() < minimum) || (maximum != null && number.value() > maximum)) {
                    issues.add(QualityIssue.open(context.studyId(), null, QualityIssueType.INVALID_RANGE,
                            QualitySeverity.ERROR, response.id(), question.id(),
                            "Value for \"" + question.label() + "\" falls outside the configured range ("
                                    + describe(minimum) + " to " + describe(maximum) + ")."));
                }
            }
        }
        return issues;
    }

    private static String describe(Double bound) {
        return bound == null ? "unbounded" : String.valueOf(bound);
    }
}
