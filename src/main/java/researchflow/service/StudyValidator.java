package researchflow.service;

import researchflow.domain.Study;

import java.util.LinkedHashMap;

public final class StudyValidator {
    public void validate(Study study) {
        var errors = new LinkedHashMap<String, String>();
        requiredAndLength(errors, "title", study.title(), 160, "Study title is required.");
        length(errors, "description", study.description(), 4_000);
        length(errors, "objectives", study.objectives(), 4_000);
        length(errors, "researcher", study.researcher(), 160);
        if (study.startDate() != null && study.endDate() != null && study.endDate().isBefore(study.startDate())) {
            errors.put("endDate", "End date cannot be before the start date.");
        }
        if (study.researchQuestions().size() > 20) {
            errors.put("researchQuestions", "A study can contain at most 20 research questions in Phase 1.");
        } else if (study.researchQuestions().stream().anyMatch(question -> question.length() > 1_000)) {
            errors.put("researchQuestions", "Each research question must be 1,000 characters or fewer.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private static void requiredAndLength(LinkedHashMap<String, String> errors, String field, String value,
                                          int max, String requiredMessage) {
        if (value.isBlank()) {
            errors.put(field, requiredMessage);
        } else {
            length(errors, field, value, max);
        }
    }

    private static void length(LinkedHashMap<String, String> errors, String field, String value, int max) {
        if (value.length() > max) {
            errors.put(field, "Must be " + max + " characters or fewer.");
        }
    }
}

