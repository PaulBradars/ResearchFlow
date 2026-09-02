package researchflow.service;

import researchflow.domain.DatasetFilterOperator;
import researchflow.domain.DatasetPage;
import researchflow.domain.DatasetQuery;
import researchflow.domain.DatasetRow;
import researchflow.domain.DatasetSort;
import researchflow.domain.QuestionType;
import researchflow.persistence.DatasetRepository;
import researchflow.persistence.FormRepository;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.UUID;

public final class DatasetService {
    private final DatasetRepository datasets;
    private final FormRepository forms;

    public DatasetService(DatasetRepository datasets, FormRepository forms) {
        this.datasets = datasets;
        this.forms = forms;
    }

    public DatasetPage query(UUID studyId, DatasetQuery query) {
        var question = query.filterQuestionId() == null ? null : findQuestion(studyId, query.filterQuestionId());
        if (question != null && query.filterOperator() != null && query.filterOperator() != DatasetFilterOperator.IS_MISSING) {
            if (query.filterValue().isBlank()) throw validation("filter", "Enter a filter value.");
            if ((query.filterOperator() == DatasetFilterOperator.GREATER_THAN
                    || query.filterOperator() == DatasetFilterOperator.LESS_THAN)) {
                if (question.type() == QuestionType.NUMBER) {
                    try { Double.parseDouble(query.filterValue()); }
                    catch (NumberFormatException exception) { throw validation("filter", "Enter a valid number."); }
                } else if (question.type() == QuestionType.DATE) {
                    try { LocalDate.parse(query.filterValue()); }
                    catch (DateTimeParseException exception) { throw validation("filter", "Enter a date as YYYY-MM-DD."); }
                } else throw validation("filter", "Greater/less filters require a number or date variable.");
            }
        }
        if ((query.sort() == DatasetSort.VARIABLE_ASC || query.sort() == DatasetSort.VARIABLE_DESC)
                && query.sortQuestionId() == null) throw validation("sort", "Choose a variable for variable sorting.");
        if (query.sortQuestionId() != null) findQuestion(studyId, query.sortQuestionId());
        return datasets.query(studyId, query);
    }

    public DatasetRow detail(UUID studyId, UUID responseId) {
        return datasets.findResponse(studyId, responseId)
                .orElseThrow(() -> new IllegalArgumentException("Response not found: " + responseId));
    }

    private researchflow.domain.Question findQuestion(UUID studyId, UUID questionId) {
        return forms.findByStudy(studyId).stream().flatMap(form -> form.questions().stream())
                .filter(question -> question.id().equals(questionId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question is not part of this study."));
    }

    private static ValidationException validation(String field, String message) {
        return new ValidationException(java.util.Map.of(field, message));
    }
}
