package researchflow.persistence;

import researchflow.domain.DatasetVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersionRepository {
    researchflow.domain.DatasetFreshness freshness(UUID studyId);
    /** Snapshots every current answer/response-status for the Study into a new active version. */
    DatasetVersion createSnapshot(UUID studyId, String reason, String changeSummary);

    List<DatasetVersion> findByStudy(UUID studyId);

    Optional<DatasetVersion> findActive(UUID studyId);

    /**
     * Restores live data to match {@code targetVersionId}'s snapshot, then records that as a new
     * active version. Earlier versions, including the one restored from, are never rewritten.
     */
    DatasetVersion restore(UUID studyId, UUID targetVersionId, String reason);
}
