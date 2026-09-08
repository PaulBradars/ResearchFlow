package researchflow.dataimport;

import researchflow.domain.QuestionType;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

final class ColumnTypeInference {
    private ColumnTypeInference() { }

    static QuestionType infer(List<String> values) {
        var nonBlank = values.stream().map(String::strip).filter(value -> !value.isBlank()).toList();
        if (nonBlank.isEmpty()) return QuestionType.SHORT_TEXT;
        if (nonBlank.stream().allMatch(ColumnTypeInference::isNumeric)) return QuestionType.NUMBER;
        if (nonBlank.stream().allMatch(ColumnTypeInference::isIsoDate)) return QuestionType.DATE;
        return QuestionType.SHORT_TEXT;
    }

    private static boolean isNumeric(String value) {
        try {
            return Double.isFinite(Double.parseDouble(value));
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static boolean isIsoDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}

