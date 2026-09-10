package researchflow.analysis;

import researchflow.domain.Answer;
import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisResult;

import java.util.ArrayList;

public final class CorrelationStrategy implements AnalysisStrategy {
    @Override
    public AnalysisMethod method() {
        return AnalysisMethod.CORRELATION;
    }

    @Override
    public AnalysisResult execute(AnalysisContext context) {
        var x = context.plan().primaryVariableId();
        var y = context.plan().secondaryVariableId();
        var xs = new ArrayList<Double>();
        var ys = new ArrayList<Double>();
        for (var response : context.responses()) {
            if (response.get(x) instanceof Answer.Number valueX && response.get(y) instanceof Answer.Number valueY) {
                xs.add(valueX.value());
                ys.add(valueY.value());
            }
        }
        if (xs.size() < 2) return new AnalysisResult.Correlation(xs.size(), 0);
        return new AnalysisResult.Correlation(xs.size(), Statistics.pearson(xs, ys));
    }
}
