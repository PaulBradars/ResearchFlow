package researchflow.persistence;

import researchflow.domain.Study;
import researchflow.domain.StudyMetrics;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudyRepository {
    void save(Study study, String auditEventType);

    Optional<Study> findById(UUID id);

    List<Study> findAll(boolean includeArchived);

    StudyMetrics metrics(UUID studyId);
}
