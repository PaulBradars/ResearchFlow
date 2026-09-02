package researchflow.service;

import researchflow.domain.Form;
import researchflow.domain.QuestionType;

import java.util.LinkedHashMap;
import java.util.HashSet;

public final class FormValidator {
    public void validate(Form form) {
        var errors = new LinkedHashMap<String, String>();
        if (form.title().isBlank()) errors.put("title", "A form title is required.");
        else if (form.title().length() > 160) errors.put("title", "Use 160 characters or fewer.");
        if (form.description().length() > 2_000) errors.put("description", "Use 2,000 characters or fewer.");
        var keys = new HashSet<String>();
        for (var question : form.questions()) {
            var prefix = "question." + question.id() + ".";
            if (question.label().isBlank()) errors.put(prefix + "label", "A question label is required.");
            if (!question.variableKey().matches("[A-Za-z][A-Za-z0-9_]{0,63}"))
                errors.put(prefix + "variableKey", "Use a letter followed by letters, numbers, or underscores.");
            else if (!keys.add(question.variableKey().toLowerCase()))
                errors.put(prefix + "variableKey", "Variable keys must be unique within the form.");
            if (question.minimum() != null && question.maximum() != null && question.maximum() < question.minimum())
                errors.put(prefix + "range", "Maximum must be greater than or equal to minimum.");
            if (requiresOptions(question.type()) && question.options().size() < 2)
                errors.put(prefix + "options", "Add at least two options.");
            var optionValues = new HashSet<String>();
            if (question.options().stream().anyMatch(option -> option.label().isBlank()))
                errors.put(prefix + "options", "Option labels cannot be blank.");
            for (var option : question.options()) {
                if (option.value().indexOf('\u001f') >= 0)
                    errors.put(prefix + "options", "Option values contain an unsupported control character.");
                if (!optionValues.add(option.value().toLowerCase()))
                    errors.put(prefix + "options", "Option values must be unique.");
            }
        }
        if (!errors.isEmpty()) throw new ValidationException(errors);
    }

    private static boolean requiresOptions(QuestionType type) {
        return switch (type) {
            case SINGLE_CHOICE, MULTIPLE_CHOICE, LIKERT, RATING -> true;
            default -> false;
        };
    }
}
