package researchflow.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Question(UUID id, String variableKey, String label, String helpText, QuestionType type,
                       boolean required, Double minimum, Double maximum, List<QuestionOption> options,
                       Instant createdAt, Instant updatedAt) {
    public Question {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        variableKey = normalize(variableKey);
        label = normalize(label);
        helpText = normalize(helpText);
        options = options == null ? List.of() : List.copyOf(options);
    }

    public static Question create(String variableKey, String label, String helpText, QuestionType type,
                                  boolean required, Double minimum, Double maximum,
                                  List<QuestionOption> options) {
        var now = Instant.now();
        return new Question(UUID.randomUUID(), variableKey, label, helpText, type, required,
                minimum, maximum, options, now, now);
    }

    public Question revise(String variableKey, String label, String helpText, QuestionType type,
                           boolean required, Double minimum, Double maximum,
                           List<QuestionOption> options) {
        return new Question(id, variableKey, label, helpText, type, required, minimum, maximum,
                options, createdAt, Instant.now());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}

