package researchflow.service;

import researchflow.domain.DatasetVersion;
import researchflow.persistence.VersionRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class VersionService {
    private final VersionRepository repository;

    public VersionService(VersionRepository repository) {
        this.repository = repository;
    }

    public List<DatasetVersion> list(UUID studyId) {
        return repository.findByStudy(studyId);
    }

    public Optional<DatasetVersion> active(UUID studyId) {
        return repository.findActive(studyId);
    }

    public DatasetVersion createSnapshot(UUID studyId, String reason, String changeSummary) {
        return repository.createSnapshot(studyId, requireReason(reason), changeSummary == null ? "" : changeSummary.strip());
    }

    public DatasetVersion restore(UUID studyId, UUID versionId, String reason) {
        return repository.restore(studyId, versionId, requireReason(reason));
    }

    private static String requireReason(String reason) {
        var normalized = reason == null ? "" : reason.strip();
        if (normalized.length() < 3) throw new ValidationException(Map.of("reason", "Provide a reason of at least 3 characters."));
        if (normalized.length() > 1_000) throw new ValidationException(Map.of("reason", "Use 1,000 characters or fewer."));
        return normalized;
    }
}

