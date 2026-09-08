package researchflow.domain;

import java.util.Objects;
import java.util.UUID;

public record QuestionOption(UUID id, String value, String label) {
    public QuestionOption {
        Objects.requireNonNull(id, "id");
        value = normalize(value);
        label = normalize(label);
    }

    public static QuestionOption create(String label) {
        var normalized = normalize(label);
        return new QuestionOption(UUID.randomUUID(), normalized, normalized);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}

