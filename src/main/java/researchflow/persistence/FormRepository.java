package researchflow.persistence;

import researchflow.domain.Form;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FormRepository {
    void save(Form form, String auditEventType);
    Optional<Form> findById(UUID id);
    List<Form> findByStudy(UUID studyId);
}

