package researchflow.analysis;

import researchflow.domain.Answer;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisResult;

import java.util.ArrayList;
import java.util.Collections;

public final class NumericSummaryStrategy implements AnalysisStrategy {
    @Override
    public AnalysisMethod method() {
        return AnalysisMethod.NUMERIC_SUMMARY;
    }

    @Override
    public AnalysisResult execute(AnalysisContext context) {
        var variable = context.plan().primaryVariableId();
        var values = new ArrayList<Double>();
        int missing = 0;
        for (var response : context.responses()) {
            var answer = response.get(variable);
            if (answer instanceof Answer.Number number) values.add(number.value());
            else missing++;
        }
        if (values.isEmpty()) return new AnalysisResult.NumericSummary(0, missing, 0, 0, 0, 0, 0);
        var mean = Statistics.mean(values);
        return new AnalysisResult.NumericSummary(values.size(), missing, mean, Statistics.median(values),
                Statistics.standardDeviation(values, mean), Collections.min(values), Collections.max(values));
    }
}
