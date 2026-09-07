package researchflow.command;

import java.util.UUID;

/**
 * An explicit, named researcher decision on a {@code QualityIssue}. Each variant carries exactly
 * the data its action needs; {@code QualityReviewService} is the only code that interprets and
 * executes a command. No command deletes or silently alters data — every variant requires a
 * reason or note and results in an audited status change.
 */
public sealed interface ReviewCommand permits ReviewCommand.Correct, ReviewCommand.Exclude,
        ReviewCommand.Accept, ReviewCommand.Defer {

    UUID issueId();

    /** Apply a validated, typed replacement answer and resolve the issue that prompted it. */
    record Correct(UUID issueId, UUID studyId, UUID responseId, UUID questionId, String rawValue,
                   String reason) implements ReviewCommand { }

    /** Exclude the response from the dataset (schema-level status, never a delete) and resolve the issue. */
    record Exclude(UUID issueId, UUID studyId, UUID responseId, String reason) implements ReviewCommand { }

    /** Retain the data as-is; the condition is acceptable and should not be re-reported as new. */
    record Accept(UUID issueId, String note) implements ReviewCommand { }

    /** Postpone a decision; marks the issue DEFERRED so it is not re-reported as new but remains for later review. */
    record Defer(UUID issueId, String note) implements ReviewCommand { }
}
