package researchflow.domain;

import java.time.Instant;
import java.util.UUID;

/** One turn of an "Ask Your Data" conversation, optionally linked to the analysis it produced. */
public record ChatMessage(UUID id, UUID studyId, UUID analysisId, ChatRole role, String content, Instant createdAt) {
    public static ChatMessage user(UUID studyId, UUID analysisId, String content) {
        return new ChatMessage(UUID.randomUUID(), studyId, analysisId, ChatRole.USER, content, Instant.now());
    }

    public static ChatMessage assistant(UUID studyId, UUID analysisId, String content) {
        return new ChatMessage(UUID.randomUUID(), studyId, analysisId, ChatRole.ASSISTANT, content, Instant.now());
    }
}
