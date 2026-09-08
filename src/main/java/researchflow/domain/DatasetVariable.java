package researchflow.domain;

import java.util.List;
import java.util.UUID;

public record DatasetVariable(UUID questionId, UUID formId, String formTitle, String variableKey,
                              String label, QuestionType type, boolean required, Double minimum,
                              Double maximum, List<String> options) {
    public DatasetVariable { options = List.copyOf(options); }
    @Override public String toString() { return formTitle + " · " + label; }
}

