package researchflow.persistence;

import researchflow.domain.QualityIssue;
import researchflow.domain.QualityIssueStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QualityRepository {
    /** Inserts every detected issue whose fingerprint does not match an already-active issue. Never removes data. */
    void reconcile(UUID studyId, List<QualityIssue> detected);

    /** @param status {@code null} returns issues in every status. */
    List<QualityIssue> findByStudy(UUID studyId, QualityIssueStatus status);

    Optional<QualityIssue> findById(UUID issueId);

    /** Atomically corrects the answer, records history/audits, and resolves the matching issue. */
    void correctAndResolve(UUID issueId, researchflow.domain.CorrectionTarget target,
                           researchflow.domain.Answer replacement, String reason);

    void markResolved(UUID issueId, String resolutionNote);

    void markAccepted(UUID issueId, String note);

    void markDeferred(UUID issueId, String note);

    /** Sets the response to EXCLUDED and resolves the issue in one transaction, with an audit event. */
    void excludeResponse(UUID studyId, UUID issueId, UUID responseId, String reason);
}
