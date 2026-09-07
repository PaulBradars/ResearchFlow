package researchflow.dataimport;

import org.junit.jupiter.api.Test;
import researchflow.domain.QuestionType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ColumnTypeInferenceTest {
    @Test
    void infersNumberWhenEveryNonBlankValueIsNumeric() {
        assertEquals(QuestionType.NUMBER, ColumnTypeInference.infer(List.of("5", "6.5", "", "8")));
    }

    @Test
    void infersDateWhenEveryNonBlankValueIsAnIsoDate() {
        assertEquals(QuestionType.DATE, ColumnTypeInference.infer(List.of("2026-01-01", "2026-02-14")));
    }

    @Test
    void fallsBackToShortTextWhenValuesAreMixedOrNonNumeric() {
        assertEquals(QuestionType.SHORT_TEXT, ColumnTypeInference.infer(List.of("5", "not a number")));
        assertEquals(QuestionType.SHORT_TEXT, ColumnTypeInference.infer(List.of("Morning", "Evening")));
    }

    @Test
    void fallsBackToShortTextWhenAllValuesAreBlank() {
        assertEquals(QuestionType.SHORT_TEXT, ColumnTypeInference.infer(List.of("", "", "  ")));
    }
}
