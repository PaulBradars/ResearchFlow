package researchflow.domain;

import java.time.Instant;
import java.util.UUID;

public record DatasetVersion(UUID id, UUID studyId, UUID parentVersionId, int versionNumber,
                             String reason, String changeSummary, Instant createdAt, boolean active) { }
