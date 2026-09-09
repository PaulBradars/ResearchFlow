package researchflow.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A reviewable, deterministically detected data-quality problem. Detection never changes data;
 * only a researcher review action (see {@code researchflow.command.ReviewCommand}) changes status.
 */
public record QualityIssue(UUID id, UUID studyId, UUID datasetVersionId, QualityIssueType type,
                           QualitySeverity severity, QualityIssueStatus status, UUID responseId,
                           UUID questionId, String explanation, String resolutionNote,
                           Instant createdAt, Instant resolvedAt) {

    public static QualityIssue open(UUID studyId, UUID datasetVersionId, QualityIssueType type,
                                    QualitySeverity severity, UUID responseId, UUID questionId, String explanation) {
        return new QualityIssue(UUID.randomUUID(), studyId, datasetVersionId, type, severity,
                QualityIssueStatus.OPEN, responseId, questionId, explanation, null, Instant.now(), null);
    }

    /** A stable key used to avoid re-reporting a still-active issue on every scan. */
    public String fingerprint() {
        return type.name() + '|' + responseId + '|' + questionId;
    }
}
