package researchflow.analysis;

import org.junit.jupiter.api.Test;
import researchflow.domain.AnalysisFilter;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.DatasetFilterOperator;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;
import researchflow.service.ValidationException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisPlanValidatorTest {
    private final Question number = Question.create("age", "Age", "", QuestionType.NUMBER, true, 0d, 120d, List.of());
    private final Question otherNumber = Question.create("score", "Score", "", QuestionType.NUMBER, true, null, null, List.of());
    private final Question text = Question.create("note", "Note", "", QuestionType.SHORT_TEXT, false, null, null, List.of());
    private final Question choice = Question.create("study_time", "Study time", "", QuestionType.SINGLE_CHOICE, true,
            null, null, List.of());
    private final Question otherChoice = Question.create("mood", "Mood", "", QuestionType.YES_NO, true, null, null, List.of());
    private final Map<UUID, Question> questions = Map.of(number.id(), number, otherNumber.id(), otherNumber,
            text.id(), text, choice.id(), choice, otherChoice.id(), otherChoice);

    @Test
    void acceptsEachMethodWithCompatibleVariableTypes() {
        assertDoesNotThrow(() -> AnalysisPlanValidator.validate(
                new AnalysisPlan(AnalysisMethod.FREQUENCY, choice.id(), null, List.of(), null), questions));
        assertDoesNotThrow(() -> AnalysisPlanValidator.validate(
                new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, number.id(), null, List.of(), null), questions));
        assertDoesNotThrow(() -> AnalysisPlanValidator.validate(
                new AnalysisPlan(AnalysisMethod.CORRELATION, number.id(), otherNumber.id(), List.of(), null), questions));
        assertDoesNotThrow(() -> AnalysisPlanValidator.validate(
                new AnalysisPlan(AnalysisMethod.CROSS_TABULATION, choice.id(), otherChoice.id(), List.of(), null), questions));
    }

    @Test
    void rejectsNumericSummaryOnANonNumberVariable() {
        assertThrows(ValidationException.class, () -> AnalysisPlanValidator.validate(
                new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, text.id(), null, List.of(), null), questions));
    }

    @Test
    void rejectsCorrelationWhenEitherVariableIsNotANumber() {
        assertThrows(ValidationException.class, () -> AnalysisPlanValidator.validate(
                new AnalysisPlan(AnalysisMethod.CORRELATION, number.id(), text.id(), List.of(), null), questions));
    }

    @Test
    void rejectsCrossTabulationOnANumberVariable() {
        assertThrows(ValidationException.class, () -> AnalysisPlanValidator.validate(
                new AnalysisPlan(AnalysisMethod.CROSS_TABULATION, number.id(), choice.id(), List.of(), null), questions));
    }

    @Test
    void rejectsTheSameVariableTwice() {
        assertThrows(ValidationException.class, () -> AnalysisPlanValidator.validate(
                new AnalysisPlan(AnalysisMethod.CORRELATION, number.id(), number.id(), List.of(), null), questions));
    }

    @Test
    void rejectsAVariableOutsideTheStudy() {
        assertThrows(ValidationException.class, () -> AnalysisPlanValidator.validate(
                new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, UUID.randomUUID(), null, List.of(), null), questions));
    }

    @Test
    void rejectsAGreaterThanFilterOnATextVariable() {
        var plan = new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, number.id(), null,
                List.of(new AnalysisFilter(text.id(), DatasetFilterOperator.GREATER_THAN, "5")), null);
        assertThrows(ValidationException.class, () -> AnalysisPlanValidator.validate(plan, questions));
    }

    @Test
    void acceptsAnIsMissingFilterWithNoValue() {
        var plan = new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, number.id(), null,
                List.of(new AnalysisFilter(text.id(), DatasetFilterOperator.IS_MISSING, null)), null);
        assertDoesNotThrow(() -> AnalysisPlanValidator.validate(plan, questions));
    }
}

