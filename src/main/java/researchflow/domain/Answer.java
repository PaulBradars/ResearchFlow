package researchflow.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public sealed interface Answer permits Answer.Text, Answer.Number, Answer.BooleanValue,
        Answer.DateValue, Answer.Choice {
    UUID id();
    UUID questionId();
    Instant createdAt();

    record Text(UUID id, UUID questionId, String value, Instant createdAt) implements Answer { }
    record Number(UUID id, UUID questionId, double value, Instant createdAt) implements Answer { }
    record BooleanValue(UUID id, UUID questionId, boolean value, Instant createdAt) implements Answer { }
    record DateValue(UUID id, UUID questionId, LocalDate value, Instant createdAt) implements Answer { }
    record Choice(UUID id, UUID questionId, List<String> values, Instant createdAt) implements Answer {
        public Choice {
            values = List.copyOf(values);
        }
    }
}
