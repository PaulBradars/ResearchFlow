package researchflow.domain;

import java.util.List;
import java.util.UUID;

public record EvidenceBundle(UUID id, AnalysisMethod method, List<VariableRef> variables, List<AnalysisFilter> filters,
                             int sampleSize, AnalysisResult result, List<String> warnings, UUID datasetVersionId) {
    public EvidenceBundle {
        variables = List.copyOf(variables);
        filters = List.copyOf(filters);
        warnings = List.copyOf(warnings);
    }

    public record VariableRef(UUID questionId, String label) { }
}

