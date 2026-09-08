package researchflow.persistence;

import researchflow.domain.DatasetVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersionRepository {

    DatasetVersion createSnapshot(UUID studyId, String reason, String changeSummary);

    List<DatasetVersion> findByStudy(UUID studyId);

    Optional<DatasetVersion> findActive(UUID studyId);

    DatasetVersion restore(UUID studyId, UUID targetVersionId, String reason);
}

