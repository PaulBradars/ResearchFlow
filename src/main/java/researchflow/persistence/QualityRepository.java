package researchflow.persistence;

import researchflow.domain.QualityIssue;
import researchflow.domain.QualityIssueStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QualityRepository {

    void reconcile(UUID studyId, List<QualityIssue> detected);

    List<QualityIssue> findByStudy(UUID studyId, QualityIssueStatus status);

    Optional<QualityIssue> findById(UUID issueId);

    void markResolved(UUID issueId, String resolutionNote);

    void markAccepted(UUID issueId, String note);

    void markDeferred(UUID issueId, String note);

    void excludeResponse(UUID studyId, UUID issueId, UUID responseId, String reason);
}

