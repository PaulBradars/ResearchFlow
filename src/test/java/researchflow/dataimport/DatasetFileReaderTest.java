package researchflow.dataimport;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DatasetFileReaderTest {
    @TempDir Path directory;

    @Test void tsvPreservesQuotedTabsCommasMultilineAndBom() throws Exception {
        var file = directory.resolve("table.tsv");
        Files.writeString(file, "\uFEFFname\tnote\nAda\t\"hello, world\tsecond\nline\"\n");
        var document = DatasetFileReader.read(file, null);
        assertEquals(List.of("name", "note"), document.headers());
        assertEquals(List.of("Ada", "hello, world\tsecond\nline"), document.rows().getFirst());
        assertThrows(ImportException.class, () -> CsvParser.parse("name\n\"unclosed"));
    }

    @Test void jsonUnionsColumnsHandlesNullsAndPreservesLargeIntegerText() throws Exception {
        var file = directory.resolve("table.json");
        Files.writeString(file, """
                {"data":[{"name":"Ada","id":9007199254740993,"value":null},
                {"name":"Grace","extra":true}]}
                """);
        var document = DatasetFileReader.read(file, null);
        assertEquals(List.of("name", "id", "value", "extra"), document.headers());
        assertEquals(List.of("Ada", "9007199254740993", "", ""), document.rows().getFirst());
        assertEquals(List.of("Grace", "", "", "true"), document.rows().get(1));
        for (var invalid : List.of("[]", "[1]", "[{\"x\":{}}]", "[{\"x\":1,\"x\":2}]", "[{\"x\":1}] trailing")) {
            Files.writeString(file, invalid);
            assertThrows(ImportException.class, () -> DatasetFileReader.read(file, null), invalid);
        }
    }

    @Test void bothExcelFormatsSupportWorksheetSelectionDatesBlanksAndSavedFormulas() throws Exception {
        for (var extension : List.of("xlsx", "xls")) {
            var file = directory.resolve("table." + extension);
            try (Workbook workbook = extension.equals("xlsx") ? new XSSFWorkbook() : new HSSFWorkbook()) {
                workbook.createSheet("Empty");
                var sheet = workbook.createSheet("Responses");
                var headers = sheet.createRow(2);
                for (int i = 0; i < 4; i++) headers.createCell(i).setCellValue(List.of("name", "age", "date", "score").get(i));
                var row = sheet.createRow(3);
                row.createCell(0).setCellValue("Ada"); row.createCell(1).setCellValue(24);
                var date = row.createCell(2); date.setCellValue(LocalDateTime.of(2026, 9, 10, 0, 0));
                var style = workbook.createCellStyle(); style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
                date.setCellStyle(style);
                row.createCell(3).setCellFormula("B4*2");
                workbook.getCreationHelper().createFormulaEvaluator().evaluateAll();
                sheet.createRow(5).createCell(0).setCellValue("Grace");
                try (var output = Files.newOutputStream(file)) { workbook.write(output); }
            }
            assertEquals(List.of("Empty", "Responses"), DatasetFileReader.sheets(file));
            assertThrows(ImportException.class, () -> DatasetFileReader.read(file, "Empty"));
            var document = DatasetFileReader.read(file, "Responses");
            assertEquals(List.of("Ada", "24", "2026-09-10", "48"), document.rows().getFirst());
            assertEquals(List.of("Grace", "", "", ""), document.rows().get(1));
            assertThrows(ImportException.class, () -> DatasetFileReader.read(file, "Missing"));
        }
    }
}
