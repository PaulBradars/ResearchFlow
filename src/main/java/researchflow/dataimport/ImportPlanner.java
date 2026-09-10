package researchflow.dataimport;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class ImportPlanner {
    private ImportPlanner() { }

    public static List<ImportColumnPlan> planColumns(CsvDocument document) {
        var usedKeys = new HashSet<String>();
        var plans = new ArrayList<ImportColumnPlan>();
        for (int index = 0; index < document.headers().size(); index++) {
            var header = document.headers().get(index);
            var columnValues = new ArrayList<String>();
            for (var row : document.rows()) columnValues.add(index < row.size() ? row.get(index) : "");
            var type = ColumnTypeInference.infer(columnValues);
            var displayHeader = header == null ? "" : header.strip();
            var variableKey = VariableKeys.sanitize(displayHeader.isBlank() ? "column_" + (index + 1) : displayHeader, usedKeys);
            var label = displayHeader.isBlank() ? "Column " + (index + 1) : displayHeader;
            plans.add(new ImportColumnPlan(index, displayHeader, variableKey, label, type, false, true));
        }
        return List.copyOf(plans);
    }
}
