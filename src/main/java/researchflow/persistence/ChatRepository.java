package researchflow.persistence;

import researchflow.domain.ChatMessage;

import java.util.List;
import java.util.UUID;

public interface ChatRepository {
    void save(ChatMessage message);

    List<ChatMessage> findByStudy(UUID studyId, int limit);
}

