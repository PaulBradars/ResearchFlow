package researchflow.service;

import researchflow.command.ReviewCommand;
import researchflow.domain.QualityIssue;
import researchflow.persistence.QualityRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class QualityReviewService {
    private final QualityRepository quality;
    private final DatasetCorrectionService corrections;

    public QualityReviewService(QualityRepository quality, DatasetCorrectionService corrections) {
        this.quality = quality;
        this.corrections = corrections;
    }

    public QualityIssue apply(ReviewCommand command) {
        var issue = quality.findById(command.issueId())
                .orElseThrow(() -> new IllegalArgumentException("The quality issue no longer exists."));
        switch (command) {
            case ReviewCommand.Correct correct -> {
                corrections.correct(correct.studyId(), correct.responseId(), correct.questionId(),
                        correct.rawValue(), correct.reason());
                quality.markResolved(issue.id(), correct.reason());
            }
            case ReviewCommand.Exclude exclude ->
                    quality.excludeResponse(exclude.studyId(), issue.id(), exclude.responseId(),
                            requireText(exclude.reason(), "reason"));
            case ReviewCommand.Accept accept -> quality.markAccepted(issue.id(), requireText(accept.note(), "note"));
            case ReviewCommand.Defer defer -> quality.markDeferred(issue.id(), requireText(defer.note(), "note"));
        }
        return quality.findById(issue.id()).orElseThrow();
    }

    public List<QualityIssue> applyAll(List<ReviewCommand> commands) {
        var results = new ArrayList<QualityIssue>();
        for (var command : commands) results.add(apply(command));
        return List.copyOf(results);
    }

    private static String requireText(String value, String field) {
        var normalized = value == null ? "" : value.strip();
        if (normalized.length() < 3) throw new ValidationException(Map.of(field, "Provide at least 3 characters."));
        if (normalized.length() > 1_000) throw new ValidationException(Map.of(field, "Use 1,000 characters or fewer."));
        return normalized;
    }
}

