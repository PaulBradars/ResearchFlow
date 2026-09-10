package researchflow.analysis;

import researchflow.domain.Answer;
import researchflow.domain.AnalysisFilter;
import researchflow.domain.DatasetFilterOperator;
import researchflow.domain.Question;
import researchflow.domain.VersionAnswer;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnalysisData {
    private AnalysisData() { }

    public static List<Map<UUID, Answer>> group(List<VersionAnswer> raw) {
        var excludedResponses = new HashSet<UUID>();
        for (var row : raw) if (row.excluded()) excludedResponses.add(row.responseId());
        var byResponse = new LinkedHashMap<UUID, Map<UUID, Answer>>();
        for (var row : raw) {
            if (excludedResponses.contains(row.responseId())) continue;
            var answers = byResponse.computeIfAbsent(row.responseId(), id -> new LinkedHashMap<>());
            if (row.answer() != null) answers.put(row.questionId(), row.answer());
        }
        return List.copyOf(byResponse.values());
    }

    public static List<Map<UUID, Answer>> applyFilters(List<Map<UUID, Answer>> responses, List<AnalysisFilter> filters,
                                                        Map<UUID, Question> questions) {
        if (filters.isEmpty()) return responses;
        var kept = new ArrayList<Map<UUID, Answer>>();
        for (var response : responses) {
            if (filters.stream().allMatch(filter -> matches(response, filter, questions))) kept.add(response);
        }
        return List.copyOf(kept);
    }

    private static boolean matches(Map<UUID, Answer> response, AnalysisFilter filter, Map<UUID, Question> questions) {
        var answer = response.get(filter.questionId());
        if (filter.operator() == DatasetFilterOperator.IS_MISSING) return answer == null;
        if (answer == null) return false;
        return switch (filter.operator()) {
            case CONTAINS -> AnalysisSupport.displayValue(answer).toLowerCase().contains(filter.value().toLowerCase());
            case EQUALS -> AnalysisSupport.displayValue(answer).equalsIgnoreCase(filter.value());
            case GREATER_THAN -> compare(answer, filter.value()) > 0;
            case LESS_THAN -> compare(answer, filter.value()) < 0;
            case IS_MISSING -> false;
        };
    }

    private static int compare(Answer answer, String filterValue) {
        if (answer instanceof Answer.Number number) return Double.compare(number.value(), Double.parseDouble(filterValue));
        if (answer instanceof Answer.DateValue date) return date.value().compareTo(LocalDate.parse(filterValue));
        return 0;
    }
}
