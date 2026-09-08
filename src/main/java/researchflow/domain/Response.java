package researchflow.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Response(UUID id, UUID formId, int formVersion, Instant startedAt, Instant submittedAt,
                       Long durationSeconds, List<Answer> answers) {
    public Response {
        answers = List.copyOf(answers);
    }

    public static Response submit(Form form, Instant startedAt, List<Answer> answers) {
        var submittedAt = Instant.now();
        Long duration = startedAt == null ? null : Math.max(0, submittedAt.getEpochSecond() - startedAt.getEpochSecond());
        return new Response(UUID.randomUUID(), form.id(), form.version(), startedAt, submittedAt, duration, answers);
    }
}

