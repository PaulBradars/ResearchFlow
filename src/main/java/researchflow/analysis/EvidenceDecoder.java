package researchflow.analysis;

import researchflow.domain.*;
import researchflow.util.Json;
import java.util.*;

import static researchflow.util.Json.*;

public final class EvidenceDecoder {
    private EvidenceDecoder() { }
    public static HistoricalEvidence decode(StoredAnalysis stored) {
        try {
            if (stored.schemaVersion() < 0 || stored.schemaVersion() > 1) throw new IllegalArgumentException("Unsupported evidence schema version.");
            var planJson = object(Json.parse(stored.planJson()));
            if (!stored.method().name().equals(string(planJson.get("method")))) throw new IllegalArgumentException("Method mismatch.");
            var primary = UUID.fromString(string(planJson.get("primaryVariableId")));
            var secondary = planJson.containsKey("secondaryVariableId") ? UUID.fromString(string(planJson.get("secondaryVariableId"))) : null;
            var filters = new ArrayList<AnalysisFilter>();
            for (var item : array(planJson.get("filters"))) {
                var filter = object(item);
                filters.add(new AnalysisFilter(UUID.fromString(string(filter.get("questionId"))),
                        DatasetFilterOperator.valueOf(string(filter.get("operator"))), string(filter.get("value"))));
            }
            var plan = new AnalysisPlan(stored.method(), primary, secondary, filters, stored.datasetVersionId());
            var variables = new ArrayList<EvidenceBundle.VariableRef>();
            String notice = "";
            if (stored.schemaVersion() == 0) {
                variables.add(new EvidenceBundle.VariableRef(primary, primary.toString()));
                if (secondary != null) variables.add(new EvidenceBundle.VariableRef(secondary, secondary.toString()));
                notice = "Legacy evidence: original variable labels were not saved; identifiers are displayed instead.";
            } else {
                for (var item : array(Json.parse(stored.variablesJson()))) {
                    var variable = object(item);
                    variables.add(new EvidenceBundle.VariableRef(UUID.fromString(string(variable.get("id"))), string(variable.get("label"))));
                }
                var expected = secondary == null ? List.of(primary) : List.of(primary, secondary);
                if (!variables.stream().map(EvidenceBundle.VariableRef::questionId).toList().equals(expected)) throw new IllegalArgumentException("Variable provenance mismatch.");
            }
            var result = result(stored.method(), object(Json.parse(stored.resultJson())));
            if (AnalysisResults.sampleSizeOf(result) != stored.sampleSize()) throw new IllegalArgumentException("Sample size mismatch.");
            var warnings = array(Json.parse(stored.warningsJson())).stream().map(Json::string).toList();
            return new HistoricalEvidence(stored, plan, new EvidenceBundle(stored.id(), stored.method(), variables, filters,
                    stored.sampleSize(), result, warnings, stored.datasetVersionId()), notice);
        } catch (RuntimeException failure) {
            return new HistoricalEvidence(stored, null, null, "Historical evidence cannot be reconstructed: " + failure.getMessage());
        }
    }

    private static AnalysisResult result(AnalysisMethod method, Map<String, Object> r) {
        var expected = switch (method) {
            case FREQUENCY -> "frequency"; case NUMERIC_SUMMARY -> "numeric_summary"; case CORRELATION -> "correlation";
            case CROSS_TABULATION -> "cross_tabulation"; case GROUP_COMPARISON -> "group_comparison";
        };
        if (!expected.equals(string(r.get("type")))) throw new IllegalArgumentException("Result type mismatch.");
        return switch (method) {
            case FREQUENCY -> new AnalysisResult.Frequency(array(r.get("categories")).stream().map(item -> {
                var c = object(item); return new AnalysisResult.Frequency.Category(string(c.get("value")), count(c.get("count")), number(c.get("percentage")));
            }).toList(), count(r.get("totalCount")), count(r.get("missingCount")));
            case NUMERIC_SUMMARY -> new AnalysisResult.NumericSummary(count(r.get("count")), count(r.get("missingCount")),
                    number(r.get("mean")), number(r.get("median")), number(r.get("standardDeviation")), number(r.get("minimum")), number(r.get("maximum")));
            case CORRELATION -> new AnalysisResult.Correlation(count(r.get("count")), number(r.get("coefficient")));
            case CROSS_TABULATION -> {
                var rows = array(r.get("rowLabels")).stream().map(Json::string).toList();
                var columns = array(r.get("columnLabels")).stream().map(Json::string).toList();
                var counts = array(r.get("counts")).stream().map(row -> array(row).stream().map(Json::count).toList()).toList();
                if (counts.size() != rows.size() || counts.stream().anyMatch(row -> row.size() != columns.size())) throw new IllegalArgumentException("Invalid table dimensions.");
                yield new AnalysisResult.CrossTabulation(rows, columns, counts, count(r.get("totalCount")));
            }
            case GROUP_COMPARISON -> new AnalysisResult.GroupComparison(string(r.get("groupALabel")), count(r.get("groupACount")),
                    number(r.get("groupAMean")), number(r.get("groupASD")), string(r.get("groupBLabel")), count(r.get("groupBCount")),
                    number(r.get("groupBMean")), number(r.get("groupBSD")), number(r.get("meanDifference")), number(r.get("tStatistic")),
                    number(r.get("degreesOfFreedom")), number(r.get("cohensD")));
        };
    }
}
