package researchflow.domain;

import java.time.Instant;
import java.util.UUID;

public record ResponseSummary(UUID id, UUID formId, String formTitle, Instant submittedAt,
                              Long durationSeconds, int answerCount) { }
