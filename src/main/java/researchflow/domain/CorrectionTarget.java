package researchflow.domain;

import java.util.UUID;

public record CorrectionTarget(UUID studyId, UUID formId, UUID responseId, UUID answerId, UUID questionId) { }
