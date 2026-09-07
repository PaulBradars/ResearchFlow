package researchflow.analysis;

import researchflow.domain.Answer;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisResult;
import researchflow.service.ValidationException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compares a NUMBER outcome across exactly two groups defined by a categorical variable: group
 * n/mean/SD, mean difference, Welch's t-statistic, its degrees of freedom, and Cohen's d.
 */
public final class GroupComparisonStrategy implements AnalysisStrategy {
    @Override
    public AnalysisMethod method() {
        return AnalysisMethod.GROUP_COMPARISON;
    }

    @Override
    public AnalysisResult execute(AnalysisContext context) {
        var outcome = context.plan().primaryVariableId();
        var group = context.plan().secondaryVariableId();
        var byGroup = new LinkedHashMap<String, List<Double>>();
        for (var response : context.responses()) {
            var outcomeAnswer = response.get(outcome);
            var groupAnswer = response.get(group);
            if (outcomeAnswer instanceof Answer.Number number && groupAnswer != null) {
                byGroup.computeIfAbsent(AnalysisSupport.displayValue(groupAnswer), key -> new ArrayList<>()).add(number.value());
            }
        }
        var groups = byGroup.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList();
        if (groups.size() != 2) {
            throw new ValidationException(Map.of("secondaryVariable",
                    "Group comparison requires exactly two groups with data; found " + groups.size() + "."));
        }
        var groupA = groups.get(0);
        var groupB = groups.get(1);
        if (groupA.getValue().size() < 2 || groupB.getValue().size() < 2) {
            throw new ValidationException(Map.of("secondaryVariable",
                    "Each group needs at least two responses for a comparison."));
        }
        return Statistics.compareGroups(groupA.getKey(), groupA.getValue(), groupB.getKey(), groupB.getValue());
    }
}
