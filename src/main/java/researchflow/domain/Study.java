package researchflow.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Study(
        UUID id,
        String title,
        String description,
        String objectives,
        String researcher,
        LocalDate startDate,
        LocalDate endDate,
        StudyStatus status,
        List<String> researchQuestions,
        Instant createdAt,
        Instant updatedAt
) {
    public Study {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        title = normalize(title);
        description = normalize(description);
        objectives = normalize(objectives);
        researcher = normalize(researcher);
        researchQuestions = researchQuestions == null
                ? List.of()
                : researchQuestions.stream().map(Study::normalize).filter(value -> !value.isBlank()).toList();
    }

    public static Study create(String title, String description, String objectives, String researcher,
                               LocalDate startDate, LocalDate endDate, List<String> researchQuestions) {
        var now = Instant.now();
        return new Study(UUID.randomUUID(), title, description, objectives, researcher, startDate, endDate,
                StudyStatus.ACTIVE, researchQuestions, now, now);
    }

    public Study revise(String title, String description, String objectives, String researcher,
                        LocalDate startDate, LocalDate endDate, List<String> questions) {
        return new Study(id, title, description, objectives, researcher, startDate, endDate,
                status, questions, createdAt, Instant.now());
    }

    public Study archive() {
        return new Study(id, title, description, objectives, researcher, startDate, endDate,
                StudyStatus.ARCHIVED, researchQuestions, createdAt, Instant.now());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
