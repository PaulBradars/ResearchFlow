package researchflow.domain;

import java.time.Instant;
import java.util.UUID;

public record AuditEvent(UUID id, String eventType, String entityType, UUID entityId,
                         String actor, Instant occurredAt, String detailsJson) { }

