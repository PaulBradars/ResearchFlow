package researchflow.dataimport;

import java.util.List;

/** A parsed CSV file: the header row and every subsequent non-blank data row, as raw strings. */
public record CsvDocument(List<String> headers, List<List<String>> rows) {
    public CsvDocument {
        headers = List.copyOf(headers);
        rows = rows.stream().map(List::copyOf).toList();
    }
}
