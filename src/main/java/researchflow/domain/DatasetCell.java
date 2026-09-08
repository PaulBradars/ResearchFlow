package researchflow.domain;

import java.util.UUID;

public record DatasetCell(UUID answerId, UUID questionId, String displayValue, boolean missing) {
    public static DatasetCell missing(UUID questionId) {
        return new DatasetCell(null, questionId, "—", true);
    }
}

