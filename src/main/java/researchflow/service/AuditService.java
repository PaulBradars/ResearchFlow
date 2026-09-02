package researchflow.service;

import researchflow.domain.AuditEvent;
import researchflow.persistence.AuditRepository;

import java.util.List;
import java.util.UUID;

public final class AuditService {
    private final AuditRepository repository;
    public AuditService(AuditRepository repository) { this.repository = repository; }
    public List<AuditEvent> timeline(UUID studyId) { return repository.findByStudy(studyId, 250); }
}
