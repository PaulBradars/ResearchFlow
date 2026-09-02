package researchflow.service;

import researchflow.domain.Answer;
import researchflow.domain.Question;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

final class AnswerValueParser {
    static final String MULTI_VALUE_SEPARATOR = "\u001f";
    private AnswerValueParser() { }

    static Answer parse(Question question, String raw) {
        return parse(question, raw, UUID.randomUUID());
    }

    static Answer parse(Question question, String raw, UUID answerId) {
        var now = Instant.now();
        return switch (question.type()) {
            case SHORT_TEXT -> new Answer.Text(answerId, question.id(), raw, now);
            case NUMBER -> parseNumber(question, raw, answerId, now);
            case YES_NO -> new Answer.BooleanValue(answerId, question.id(), parseBoolean(raw), now);
            case DATE -> new Answer.DateValue(answerId, question.id(), parseDate(raw), now);
            case SINGLE_CHOICE, LIKERT, RATING ->
                    new Answer.Choice(answerId, question.id(), validateChoices(question, List.of(raw)), now);
            case MULTIPLE_CHOICE -> new Answer.Choice(answerId, question.id(), validateChoices(question,
                    Arrays.stream(raw.split(MULTI_VALUE_SEPARATOR, -1)).filter(value -> !value.isBlank()).toList()), now);
        };
    }

    private static Answer.Number parseNumber(Question question, String raw, UUID id, Instant now) {
        final double value;
        try { value = Double.parseDouble(raw); }
        catch (NumberFormatException exception) { throw new IllegalArgumentException("Enter a valid number."); }
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Enter a finite number.");
        if (question.minimum() != null && value < question.minimum())
            throw new IllegalArgumentException("Enter a value of at least " + question.minimum() + ".");
        if (question.maximum() != null && value > question.maximum())
            throw new IllegalArgumentException("Enter a value no greater than " + question.maximum() + ".");
        return new Answer.Number(id, question.id(), value, now);
    }

    private static boolean parseBoolean(String raw) {
        if (raw.equalsIgnoreCase("yes") || raw.equalsIgnoreCase("true")) return true;
        if (raw.equalsIgnoreCase("no") || raw.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("Choose Yes or No.");
    }

    private static LocalDate parseDate(String raw) {
        try { return LocalDate.parse(raw); }
        catch (DateTimeParseException exception) { throw new IllegalArgumentException("Enter a valid date."); }
    }

    private static List<String> validateChoices(Question question, List<String> values) {
        if (values.isEmpty()) throw new IllegalArgumentException("Choose at least one option.");
        var allowed = question.options().stream().map(option -> option.value()).collect(java.util.stream.Collectors.toSet());
        if (new HashSet<>(values).size() != values.size() || !allowed.containsAll(values))
            throw new IllegalArgumentException("Choose only the available options.");
        return List.copyOf(values);
    }
}
