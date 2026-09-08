package researchflow.ai;

import researchflow.domain.AnalysisFilter;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisPlan;
import researchflow.domain.DatasetFilterOperator;
import researchflow.domain.Question;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class AnalysisPlanParser {
    private AnalysisPlanParser() { }

    public static AnalysisPlan parse(String raw, Map<UUID, Question> questions) {
        var json = SimpleJson.extractObject(raw);
        if (json == null) {
            throw malformed("The AI response did not contain a JSON object.");
        }

        var methodText = SimpleJson.stringField(json, "method");
        if (methodText == null || methodText.isBlank()) {
            throw malformed("The AI response did not include a method.");
        }
        AnalysisMethod method;
        try {
            method = AnalysisMethod.valueOf(methodText.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw malformed("The AI response named an unsupported method: \"" + methodText + "\".");
        }

        var primary = requireVariable(json, "primaryVariableId", questions, true);
        var secondary = requireVariable(json, "secondaryVariableId", questions, false);
        var filters = parseFilters(json, questions);
        return new AnalysisPlan(method, primary, secondary, filters, null);
    }

    private static UUID requireVariable(String json, String key, Map<UUID, Question> questions, boolean required) {
        var text = SimpleJson.stringField(json, key);
        if (text == null || text.isBlank()) {
            if (required) throw malformed("The AI response did not include a " + key + ".");
            return null;
        }
        UUID id;
        try {
            id = UUID.fromString(text.trim());
        } catch (IllegalArgumentException exception) {
            throw malformed("The AI response's " + key + " was not a valid variable ID.");
        }
        if (!questions.containsKey(id)) {
            throw malformed("The AI response referenced a variable outside this Study.");
        }
        return id;
    }

    private static List<AnalysisFilter> parseFilters(String json, Map<UUID, Question> questions) {
        var arrayJson = SimpleJson.arrayField(json, "filters");
        if (arrayJson == null) return List.of();
        var filters = new ArrayList<AnalysisFilter>();
        for (var chunk : SimpleJson.objectsIn(arrayJson)) {
            var questionId = requireVariable(chunk, "questionId", questions, true);
            var operatorText = SimpleJson.stringField(chunk, "operator");
            if (operatorText == null) throw malformed("A filter in the AI response was missing an operator.");
            DatasetFilterOperator operator;
            try {
                operator = DatasetFilterOperator.valueOf(operatorText.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw malformed("A filter in the AI response used an unsupported operator: \"" + operatorText + "\".");
            }
            var value = SimpleJson.stringField(chunk, "value");
            filters.add(new AnalysisFilter(questionId, operator, value));
        }
        return List.copyOf(filters);
    }

    private static LlmException malformed(String message) {
        return new LlmException(LlmException.Kind.MALFORMED_RESPONSE, message);
    }
}

