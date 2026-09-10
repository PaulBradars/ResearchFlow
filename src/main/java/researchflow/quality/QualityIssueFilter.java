package researchflow.quality;

import researchflow.domain.*;
import java.util.Locale;

/** All supplied criteria must match; null enum criteria mean all values. */
public record QualityIssueFilter(QualityIssueStatus status, QualityIssueType type,
                                 QualitySeverity severity, String search) {
    public boolean matches(QualityIssue issue) {
        if (status != null && issue.status() != status || type != null && issue.type() != type
                || severity != null && issue.severity() != severity) return false;
        var query = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        var text = issue.type() + " " + issue.severity() + " " + issue.status() + " " + issue.explanation()
                + " " + (issue.responseId() == null ? "" : issue.responseId())
                + " " + (issue.questionId() == null ? "" : issue.questionId())
                + " " + (issue.resolutionNote() == null ? "" : issue.resolutionNote());
        return query.isEmpty() || text.toLowerCase(Locale.ROOT).contains(query);
    }
}
