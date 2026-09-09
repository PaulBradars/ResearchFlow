package researchflow.dataimport;

import java.util.ArrayList;
import java.util.List;

/**
 * A small, hand-rolled CSV reader — comma-delimited, double-quote quoting with {@code ""} as an
 * escaped quote, and quoted fields may contain commas or newlines. Not a full RFC 4180 validator:
 * a stray quote mid-field is tolerated rather than rejected. Blank lines are skipped; ragged rows
 * (wrong column count) are left for the caller to reject per-row rather than failing the whole file.
 */
public final class CsvParser {
    private CsvParser() { }

    public static CsvDocument parse(String content) {
        var rows = new ArrayList<List<String>>();
        var currentRow = new ArrayList<String>();
        var field = new StringBuilder();
        var inQuotes = false;
        var rowStarted = false;
        var length = content.length();
        var index = 0;
        while (index < length) {
            var character = content.charAt(index);
            if (inQuotes) {
                if (character == '"') {
                    if (index + 1 < length && content.charAt(index + 1) == '"') {
                        field.append('"');
                        index += 2;
                        continue;
                    }
                    inQuotes = false;
                    index++;
                    continue;
                }
                field.append(character);
                index++;
                continue;
            }
            switch (character) {
                case '"' -> { inQuotes = true; rowStarted = true; index++; }
                case ',' -> { currentRow.add(field.toString()); field.setLength(0); rowStarted = true; index++; }
                case '\r' -> index++;
                case '\n' -> {
                    currentRow.add(field.toString());
                    field.setLength(0);
                    rows.add(currentRow);
                    currentRow = new ArrayList<>();
                    rowStarted = false;
                    index++;
                }
                default -> { field.append(character); rowStarted = true; index++; }
            }
        }
        if (rowStarted || !field.isEmpty() || !currentRow.isEmpty()) {
            currentRow.add(field.toString());
            rows.add(currentRow);
        }
        if (rows.isEmpty()) throw new ImportException("The file is empty.");

        var headers = rows.getFirst();
        var dataRows = rows.subList(1, rows.size()).stream()
                .filter(row -> row.stream().anyMatch(value -> !value.isBlank()))
                .toList();
        return new CsvDocument(headers, dataRows);
    }
}
