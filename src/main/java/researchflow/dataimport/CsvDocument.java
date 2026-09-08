package researchflow.dataimport;

import java.util.List;

public record CsvDocument(List<String> headers, List<List<String>> rows) {
    public CsvDocument {
        headers = List.copyOf(headers);
        rows = rows.stream().map(List::copyOf).toList();
    }
}

