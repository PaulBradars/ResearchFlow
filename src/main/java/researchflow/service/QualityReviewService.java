package researchflow.service;

import researchflow.command.ReviewCommand;
import researchflow.domain.QualityIssue;
import researchflow.persistence.QualityRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The sole interpreter of {@link ReviewCommand}s. Every branch requires researcher confirmation
 * upstream (enforced by the UI) plus a reason/note, and every branch results in an audited status
 * change through {@code QualityRepository} — no command deletes or silently edits data.
 */
public final class QualityReviewService {
    private final StudyWriteGuard writeGuard;
    private final QualityRepository quality;
    private final DatasetCorrectionService corrections;

    public QualityReviewService(QualityRepository quality, DatasetCorrectionService corrections, StudyWriteGuard writeGuard) {
        this.writeGuard = java.util.Objects.requireNonNull(writeGuard);
        this.quality = quality;
        this.corrections = corrections;
    }

    public QualityIssue apply(ReviewCommand command) {
        var issue = quality.findById(command.issueId())
                .orElseThrow(() -> new IllegalArgumentException("The quality issue no longer exists."));
        writeGuard.requireWritable(issue.studyId());
        if (issue.status() != researchflow.domain.QualityIssueStatus.OPEN
                && issue.status() != researchflow.domain.QualityIssueStatus.DEFERRED)
            throw new ValidationException(Map.of("status", "This issue has already been reviewed. Refresh the issue list."));
        switch (command) {
            case ReviewCommand.Correct correct -> {
                requireTarget(issue, correct.studyId(), correct.responseId(), correct.questionId(), true);
                var correction = corrections.prepare(issue.studyId(), issue.responseId(), issue.questionId(),
                        correct.rawValue(), correct.reason());
                quality.correctAndResolve(issue.id(), correction.target(), correction.replacement(), correction.reason());
            }
            case ReviewCommand.Exclude exclude -> {
                requireTarget(issue, exclude.studyId(), exclude.responseId(), null, false);
                quality.excludeResponse(issue.studyId(), issue.id(), issue.responseId(), requireText(exclude.reason(), "reason"));
            }
            case ReviewCommand.Accept accept -> quality.markAccepted(issue.id(), requireText(accept.note(), "note"));
            case ReviewCommand.Defer defer -> quality.markDeferred(issue.id(), requireText(defer.note(), "note"));
        }
        return quality.findById(issue.id()).orElseThrow();
    }

    /** Applies each command independently; a later failure does not undo earlier successful ones. */
    public List<QualityIssue> applyAll(List<ReviewCommand> commands) {
        var results = new ArrayList<QualityIssue>();
        for (var command : commands) results.add(apply(command));
        return List.copyOf(results);
    }

    private static void requireTarget(QualityIssue issue, java.util.UUID studyId, java.util.UUID responseId,
                                      java.util.UUID questionId, boolean correction) {
        if (!issue.studyId().equals(studyId) || issue.responseId() == null || !issue.responseId().equals(responseId)
                || (correction && (issue.questionId() == null || !issue.questionId().equals(questionId)))) {
            throw new IllegalArgumentException("The review target does not match the quality issue.");
        }
    }

    private static String requireText(String value, String field) {
        var normalized = value == null ? "" : value.strip();
        if (normalized.length() < 3) throw new ValidationException(Map.of(field, "Provide at least 3 characters."));
        if (normalized.length() > 1_000) throw new ValidationException(Map.of(field, "Use 1,000 characters or fewer."));
        return normalized;
    }
}
