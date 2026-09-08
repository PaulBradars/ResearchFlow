package researchflow.service;

import researchflow.domain.ResponseSummary;
import researchflow.persistence.ResponseRepository;

import java.util.List;
import java.util.UUID;

public final class ResponseQueryService {
    private final ResponseRepository repository;

    public ResponseQueryService(ResponseRepository repository) {
        this.repository = repository;
    }

    public List<ResponseSummary> list(UUID studyId) {
        return repository.findByStudy(studyId);
    }
}

