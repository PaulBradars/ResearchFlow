package researchflow.dataimport;

import java.util.List;

/** A parsed file plus its proposed (editable) column plan, held in memory between preview and confirm. */
public record ImportPreview(CsvDocument document, List<ImportColumnPlan> columns) {
    public ImportPreview {
        columns = List.copyOf(columns);
    }
}
