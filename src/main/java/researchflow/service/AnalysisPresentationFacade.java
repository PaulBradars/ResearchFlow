package researchflow.service;

import researchflow.domain.AnalysisResult;
import researchflow.domain.HistoricalEvidence;
import researchflow.visualization.ChartBuilder;
import researchflow.visualization.ChartSpec;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Structural Facade for reopening evidence, obtaining snapshot-bound chart data, and loading provenance. */
public final class AnalysisPresentationFacade {
    private final AnalysisService analysis;

    public AnalysisPresentationFacade(AnalysisService analysis) {
        this.analysis = Objects.requireNonNull(analysis, "analysis");
    }

    /** Read-only and JavaFX-independent. Call on the worker executor; render returned chart specs on the UI thread. */
    public Presentation load(UUID studyId, UUID analysisId) {
        // Reopen checks study ownership before any chart values are read.
        var historical = analysis.reopen(studyId, analysisId);
        Optional<ChartSpec> chart = Optional.empty();
        if (historical.supported()) {
            var evidence = historical.evidence();
            var result = evidence.result();
            boolean needsValues = result instanceof AnalysisResult.NumericSummary || result instanceof AnalysisResult.Correlation;
            var values = needsValues ? analysis.chartValues(studyId, evidence) : null;
            chart = ChartBuilder.build(evidence, values == null ? List.of() : values.primary(),
                    values == null ? null : values.secondary());
        }
        return new Presentation(historical, chart, analysis.provenance(studyId, analysisId));
    }

    public record Presentation(HistoricalEvidence historical, Optional<ChartSpec> chart, String provenance) {
        public Presentation {
            Objects.requireNonNull(historical, "historical");
            Objects.requireNonNull(chart, "chart");
            Objects.requireNonNull(provenance, "provenance");
        }
    }
}
