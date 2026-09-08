package researchflow.command;

import java.util.UUID;

public sealed interface ReviewCommand permits ReviewCommand.Correct, ReviewCommand.Exclude,
        ReviewCommand.Accept, ReviewCommand.Defer {

    UUID issueId();

    record Correct(UUID issueId, UUID studyId, UUID responseId, UUID questionId, String rawValue,
                   String reason) implements ReviewCommand { }

    record Exclude(UUID issueId, UUID studyId, UUID responseId, String reason) implements ReviewCommand { }

    record Accept(UUID issueId, String note) implements ReviewCommand { }

    record Defer(UUID issueId, String note) implements ReviewCommand { }
}

