package researchflow.dataimport;

import java.util.List;

public record ImportPreview(CsvDocument document, List<ImportColumnPlan> columns) {
    public ImportPreview {
        columns = List.copyOf(columns);
    }
}
