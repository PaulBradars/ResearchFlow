package researchflow.domain;

import java.time.Instant;
import java.util.UUID;

public record QualityIssue(UUID id, UUID studyId, UUID datasetVersionId, QualityIssueType type,
                           QualitySeverity severity, QualityIssueStatus status, UUID responseId,
                           UUID questionId, String explanation, String resolutionNote,
                           Instant createdAt, Instant resolvedAt) {

    public static QualityIssue open(UUID studyId, UUID datasetVersionId, QualityIssueType type,
                                    QualitySeverity severity, UUID responseId, UUID questionId, String explanation) {
        return new QualityIssue(UUID.randomUUID(), studyId, datasetVersionId, type, severity,
                QualityIssueStatus.OPEN, responseId, questionId, explanation, null, Instant.now(), null);
    }

    public String fingerprint() {
        return type.name() + '|' + responseId + '|' + questionId;
    }
}
