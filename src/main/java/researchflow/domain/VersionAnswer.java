package researchflow.domain;

import java.util.UUID;

public record VersionAnswer(UUID responseId, UUID questionId, Answer answer, boolean excluded) { }
