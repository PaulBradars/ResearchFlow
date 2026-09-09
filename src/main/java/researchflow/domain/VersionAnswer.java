package researchflow.domain;

import java.util.UUID;

/** One reconstructed, typed answer from a dataset version's immutable snapshot. {@code answer} is {@code null} when missing. A null questionId represents a response with no answers. */
public record VersionAnswer(UUID responseId, UUID questionId, Answer answer, boolean excluded) { }
