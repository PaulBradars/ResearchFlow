package researchflow.quality;

import researchflow.domain.Answer;
import researchflow.domain.QualityIssue;
import researchflow.domain.QualityIssueType;
import researchflow.domain.QualitySeverity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class MissingRequiredHandler extends QualityHandler {
    @Override
    protected List<QualityIssue> evaluate(QualityScanContext context) {
        var issues = new ArrayList<QualityIssue>();
        for (var response : context.responses()) {
            var form = context.form(response.formId()).orElse(null);
            if (form == null) continue;
            var answered = response.answers().stream()
                    .collect(Collectors.toMap(Answer::questionId, answer -> answer, (first, second) -> first));
            for (var question : form.questions()) {
                if (!question.required()) continue;
                if (isMissing(answered.get(question.id()))) {
                    issues.add(QualityIssue.open(context.studyId(), null, QualityIssueType.MISSING_REQUIRED,
                            QualitySeverity.ERROR, response.id(), question.id(),
                            "Required question \"" + question.label() + "\" has no recorded value for this response."));
                }
            }
        }
        return issues;
    }

    private static boolean isMissing(Answer answer) {
        if (answer == null) return true;
        if (answer instanceof Answer.Text text) return text.value().isBlank();
        if (answer instanceof Answer.Choice choice) return choice.values().isEmpty();
        return false;
    }
}

