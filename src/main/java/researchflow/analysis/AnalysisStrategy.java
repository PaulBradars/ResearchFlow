package researchflow.analysis;

import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisResult;

public interface AnalysisStrategy {
    AnalysisMethod method();

    AnalysisResult execute(AnalysisContext context);
}

