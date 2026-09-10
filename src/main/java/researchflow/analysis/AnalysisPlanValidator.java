package researchflow.analysis;

import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.DatasetFilterOperator;
import researchflow.domain.Question;
import researchflow.domain.QuestionType;
import researchflow.service.ValidationException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class AnalysisPlanValidator {
    private static final Set<QuestionType> CATEGORICAL = EnumSet.of(QuestionType.SINGLE_CHOICE, QuestionType.YES_NO,
            QuestionType.LIKERT, QuestionType.RATING);

    private AnalysisPlanValidator() { }

    public static void validate(AnalysisPlan plan, Map<UUID, Question> questions) {
        var errors = new LinkedHashMap<String, String>();
        var primary = questions.get(plan.primaryVariableId());
        if (primary == null) errors.put("primaryVariable", "Choose a variable that belongs to this Study.");

        var needsSecondary = plan.method() == AnalysisMethod.CORRELATION
                || plan.method() == AnalysisMethod.CROSS_TABULATION || plan.method() == AnalysisMethod.GROUP_COMPARISON;
        Question secondary = null;
        if (needsSecondary) {
            secondary = questions.get(plan.secondaryVariableId());
            if (secondary == null) errors.put("secondaryVariable", "Choose a second variable that belongs to this Study.");
            else if (primary != null && secondary.id().equals(primary.id())) {
                errors.put("secondaryVariable", "Choose two different variables.");
            }
        }

        if (primary != null) {
            var primaryNeedsNumber = plan.method() == AnalysisMethod.NUMERIC_SUMMARY
                    || plan.method() == AnalysisMethod.GROUP_COMPARISON || plan.method() == AnalysisMethod.CORRELATION;
            if (primaryNeedsNumber && primary.type() != QuestionType.NUMBER) {
                errors.put("primaryVariable", "This method requires a number variable.");
            }
            if (plan.method() == AnalysisMethod.CROSS_TABULATION && !CATEGORICAL.contains(primary.type())) {
                errors.put("primaryVariable", "This method requires a single choice, yes/no, Likert, or rating variable.");
            }
        }
        if (secondary != null) {
            if (plan.method() == AnalysisMethod.CORRELATION && secondary.type() != QuestionType.NUMBER) {
                errors.put("secondaryVariable", "Correlation requires a number variable.");
            }
            if ((plan.method() == AnalysisMethod.CROSS_TABULATION || plan.method() == AnalysisMethod.GROUP_COMPARISON)
                    && !CATEGORICAL.contains(secondary.type())) {
                errors.put("secondaryVariable", "This method requires a single choice, yes/no, Likert, or rating variable.");
            }
        }

        for (var filter : plan.filters()) {
            var question = questions.get(filter.questionId());
            if (question == null) {
                errors.put("filters", "A filter references a variable outside this Study.");
                continue;
            }
            if (filter.operator() != DatasetFilterOperator.IS_MISSING && (filter.value() == null || filter.value().isBlank())) {
                errors.put("filters", "Enter a filter value.");
                continue;
            }
            if (filter.operator() == DatasetFilterOperator.GREATER_THAN || filter.operator() == DatasetFilterOperator.LESS_THAN) {
                if (question.type() == QuestionType.NUMBER) {
                    try {
                        Double.parseDouble(filter.value());
                    } catch (NumberFormatException exception) {
                        errors.put("filters", "Enter a valid number filter value.");
                    }
                } else if (question.type() == QuestionType.DATE) {
                    try {
                        LocalDate.parse(filter.value());
                    } catch (DateTimeParseException exception) {
                        errors.put("filters", "Enter a valid date filter value.");
                    }
                } else {
                    errors.put("filters", "Greater/less filters require a number or date variable.");
                }
            }
        }

        if (!errors.isEmpty()) throw new ValidationException(errors);
    }
}
