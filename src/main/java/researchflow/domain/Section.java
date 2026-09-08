package researchflow.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Section(UUID id, String title, String description, List<Question> questions) {
    public Section {
        Objects.requireNonNull(id, "id");
        title = normalize(title);
        description = normalize(description);
        questions = questions == null ? List.of() : List.copyOf(questions);
    }

    public static Section create(String title) {
        return new Section(UUID.randomUUID(), title, "", List.of());
    }

    public Section withQuestions(List<Question> values) {
        return new Section(id, title, description, values);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}

