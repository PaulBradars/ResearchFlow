package researchflow.dataimport;

import org.apache.poi.ss.usermodel.*;
import researchflow.util.Json;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Converts supported external tables to the common column-mapping input. */
public final class DatasetFileReader {
    private DatasetFileReader() { }

    public static boolean isExcel(Path file) {
        var name = file.toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".xlsx") || name.endsWith(".xls");
    }

    private static void checkSize(Path file) throws IOException {
        if (Files.size(file) > 50_000_000) throw new ImportException("Use a file smaller than 50 MB.");
    }

    public static List<String> sheets(Path file) {
        try {
            checkSize(file);
            try (var workbook = WorkbookFactory.create(file.toFile(), null, true)) {
                var names = new ArrayList<String>();
                for (var sheet : workbook) names.add(sheet.getSheetName());
                if (names.isEmpty()) throw new ImportException("The workbook has no worksheets.");
                return List.copyOf(names);
            }
        } catch (IOException | RuntimeException failure) { throw explain(failure); }
    }

    public static CsvDocument read(Path file, String sheetName) {
        try {
            checkSize(file);
            if (isExcel(file)) return excel(file, sheetName);
            var text = Files.readString(file, StandardCharsets.UTF_8);
            if (text.startsWith("\uFEFF")) text = text.substring(1);
            var name = file.toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".csv")) return CsvParser.parse(text);
            if (name.endsWith(".tsv")) return CsvParser.parse(text, '\t');
            if (name.endsWith(".json")) return json(text);
            throw new ImportException("Choose CSV, TSV, JSON, XLSX, or XLS.");
        } catch (IOException | RuntimeException failure) { throw explain(failure); }
    }

    private static ImportException explain(Exception failure) {
        if (failure instanceof ImportException invalid) return invalid;
        return new ImportException("Could not read dataset. Check the format and ensure the workbook is not password protected. "
                + failure.getMessage(), failure);
    }

    private static CsvDocument excel(Path file, String sheetName) throws IOException {
        try (var workbook = WorkbookFactory.create(file.toFile(), null, true)) {
            if (workbook.getNumberOfSheets() == 0) throw new ImportException("The workbook has no worksheets.");
            var sheet = sheetName == null ? workbook.getSheetAt(0) : workbook.getSheet(sheetName);
            if (sheet == null) throw new ImportException("Worksheet no longer exists: " + sheetName);
            var rows = new ArrayList<List<String>>();
            var formatter = new DataFormatter(Locale.ROOT);
            int width = 0;
            for (var row : sheet) {
                if (row.getLastCellNum() > 1000 || rows.size() > 100_000)
                    throw new ImportException("Import supports up to 1,000 columns and 100,000 rows per sheet.");
                var values = new ArrayList<String>();
                for (int column = 0; column < Math.max(width, row.getLastCellNum()); column++) {
                    var cell = row.getCell(column);
                    values.add(cell == null ? "" : cellValue(cell, formatter));
                }
                if (values.stream().allMatch(String::isBlank)) continue;
                if (rows.isEmpty()) width = values.size();
                rows.add(values);
            }
            if (rows.isEmpty()) throw new ImportException("The selected worksheet is empty.");
            return new CsvDocument(rows.getFirst(), rows.subList(1, rows.size()));
        }
    }

    private static String cellValue(Cell cell, DataFormatter formatter) {
        // Read saved formula results only: importing never executes formulas or external links.
        var type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (type) {
            case STRING -> cell.getStringCellValue();
            case BOOLEAN -> Boolean.toString(cell.getBooleanCellValue());
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                    : java.math.BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
            case BLANK, _NONE -> "";
            case ERROR -> throw new ImportException("Excel error at " + cell.getAddress() + ". Fix formula errors and save the workbook before importing.");
            default -> formatter.formatCellValue(cell);
        };
    }

    private static CsvDocument json(String text) {
        final Object parsed;
        try { parsed = Json.parseExact(text); }
        catch (IllegalArgumentException invalid) { throw new ImportException("Invalid JSON (maximum 10 million characters). Use an array of row objects.", invalid); }
        Object data = parsed;
        if (data instanceof Map<?, ?> wrapper && wrapper.containsKey("data")) data = wrapper.get("data");
        if (!(data instanceof List<?> records) || records.isEmpty())
            throw new ImportException("JSON must be a non-empty array of row objects, or an object containing a data array.");
        var headers = new LinkedHashSet<String>();
        for (var record : records) {
            if (!(record instanceof Map<?, ?> row)) throw new ImportException("Every JSON row must be an object.");
            for (var key : row.keySet()) headers.add((String) key);
        }
        if (headers.isEmpty()) throw new ImportException("JSON rows contain no columns.");
        var rows = new ArrayList<List<String>>();
        for (var record : records) {
            var row = (Map<?, ?>) record;
            var values = new ArrayList<String>();
            for (var header : headers) {
                var value = row.get(header);
                if (value instanceof Map<?, ?> || value instanceof List<?>)
                    throw new ImportException("JSON row " + (rows.size() + 1) + ", column " + header + ": nested objects and arrays are not supported. Flatten them into columns first.");
                values.add(value == null ? "" : value.toString());
            }
            rows.add(values);
        }
        return new CsvDocument(List.copyOf(headers), rows);
    }
}
