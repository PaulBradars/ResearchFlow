package researchflow.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record DatasetRow(UUID responseId, UUID formId, String formTitle, String status,
                         Instant submittedAt, Long durationSeconds, Map<UUID, DatasetCell> cells) {
    public DatasetRow { cells = Map.copyOf(cells); }
    public DatasetCell cell(UUID questionId) {
        return cells.getOrDefault(questionId, DatasetCell.missing(questionId));
    }
}
