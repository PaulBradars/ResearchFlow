package researchflow.analysis;

import researchflow.domain.AnalysisMethod;
import researchflow.domain.AnalysisResult;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

public final class CrossTabulationStrategy implements AnalysisStrategy {
    @Override
    public AnalysisMethod method() {
        return AnalysisMethod.CROSS_TABULATION;
    }

    @Override
    public AnalysisResult execute(AnalysisContext context) {
        var rowVariable = context.plan().primaryVariableId();
        var columnVariable = context.plan().secondaryVariableId();
        var rowLabels = new TreeSet<String>();
        var columnLabels = new TreeSet<String>();
        var pairs = new ArrayList<String[]>();
        for (var response : context.responses()) {
            var row = response.get(rowVariable);
            var column = response.get(columnVariable);
            if (row == null || column == null) continue;
            var rowValue = AnalysisSupport.displayValue(row);
            var columnValue = AnalysisSupport.displayValue(column);
            rowLabels.add(rowValue);
            columnLabels.add(columnValue);
            pairs.add(new String[]{rowValue, columnValue});
        }
        var rows = List.copyOf(rowLabels);
        var columns = List.copyOf(columnLabels);
        var counts = new ArrayList<List<Integer>>();
        for (var row : rows) {
            var rowCounts = new ArrayList<Integer>();
            for (var column : columns) {
                var count = (int) pairs.stream().filter(pair -> pair[0].equals(row) && pair[1].equals(column)).count();
                rowCounts.add(count);
            }
            counts.add(List.copyOf(rowCounts));
        }
        return new AnalysisResult.CrossTabulation(rows, columns, List.copyOf(counts), pairs.size());
    }
}

