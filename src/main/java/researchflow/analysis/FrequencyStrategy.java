package researchflow.analysis;

import researchflow.domain.Answer;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Counts and percentages of each distinct value for one variable. Multiple-choice contributes to every selected value. */
public final class FrequencyStrategy implements AnalysisStrategy {
    @Override
    public AnalysisMethod method() {
        return AnalysisMethod.FREQUENCY;
    }

    @Override
    public AnalysisResult execute(AnalysisContext context) {
        var variable = context.plan().primaryVariableId();
        var counts = new LinkedHashMap<String, Integer>();
        int total = 0;
        int missing = 0;
        for (var response : context.responses()) {
            var answer = response.get(variable);
            if (answer == null) {
                missing++;
                continue;
            }
            total++;
            for (var value : values(answer)) counts.merge(value, 1, Integer::sum);
        }
        var respondentCount = total;
        var categories = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(entry -> new AnalysisResult.Frequency.Category(entry.getKey(), entry.getValue(),
                        respondentCount == 0 ? 0 : entry.getValue() * 100.0 / respondentCount))
                .toList();
        return new AnalysisResult.Frequency(categories, total, missing);
    }

    private static List<String> values(Answer answer) {
        if (answer instanceof Answer.Choice choice) return choice.values();
        return List.of(AnalysisSupport.displayValue(answer));
    }
}
