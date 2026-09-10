package researchflow.quality;

import researchflow.domain.QualityIssue;
import researchflow.domain.QualityIssueType;
import researchflow.domain.QualitySeverity;

import java.util.ArrayList;
import java.util.List;

public final class FastSubmissionHandler extends QualityHandler {
    private static final int SECONDS_PER_QUESTION = 2;
    private static final int MINIMUM_FLOOR_SECONDS = 5;

    @Override
    protected List<QualityIssue> evaluate(QualityScanContext context) {
        var issues = new ArrayList<QualityIssue>();
        for (var response : context.responses()) {
            if (response.durationSeconds() == null) continue;
            var form = context.form(response.formId()).orElse(null);
            if (form == null) continue;
            var threshold = Math.max(MINIMUM_FLOOR_SECONDS, form.questions().size() * SECONDS_PER_QUESTION);
            if (response.durationSeconds() < threshold) {
                issues.add(QualityIssue.open(context.studyId(), null, QualityIssueType.FAST_SUBMISSION,
                        QualitySeverity.WARNING, response.id(), null,
                        "Submitted in " + response.durationSeconds() + "s, faster than the " + threshold
                                + "s minimum expected for a " + form.questions().size() + "-question form."));
            }
        }
        return issues;
    }
}
