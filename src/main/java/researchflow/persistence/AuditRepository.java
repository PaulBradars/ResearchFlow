package researchflow.persistence;

import researchflow.domain.AuditEvent;

import java.util.List;
import java.util.UUID;

public interface AuditRepository {
    List<AuditEvent> findByStudy(UUID studyId, int limit);

    /** Records a standalone audit event not already produced by another repository's own transaction. */
    void recordEvent(UUID studyId, String eventType, String entityType, UUID entityId, String detailsJson);
}
