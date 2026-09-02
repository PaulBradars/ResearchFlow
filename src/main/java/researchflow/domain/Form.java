package researchflow.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Form(UUID id, UUID studyId, String title, String description, FormStatus status,
                   int version, List<Section> sections, Instant createdAt, Instant updatedAt) {
    public Form {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(studyId, "studyId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        title = normalize(title);
        description = normalize(description);
        sections = sections == null ? List.of() : List.copyOf(sections);
    }

    public static Form create(UUID studyId, String title, String description) {
        var now = Instant.now();
        return new Form(UUID.randomUUID(), studyId, title, description, FormStatus.DRAFT, 1,
                List.of(Section.create("Questions")), now, now);
    }

    public Form revise(String title, String description, List<Section> sections) {
        return new Form(id, studyId, title, description, status, version, sections, createdAt, Instant.now());
    }

    public Form withStatus(FormStatus next) {
        return new Form(id, studyId, title, description, next, version, sections, createdAt, Instant.now());
    }

    public List<Question> questions() {
        return sections.stream().flatMap(section -> section.questions().stream()).toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
