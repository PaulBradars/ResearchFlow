package researchflow.dataimport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CsvParserTest {
    @Test
    void parsesASimpleCommaDelimitedFile() {
        var document = CsvParser.parse("name,age\nAda,24\nGrace,36\n");

        assertEquals(java.util.List.of("name", "age"), document.headers());
        assertEquals(2, document.rows().size());
        assertEquals(java.util.List.of("Ada", "24"), document.rows().get(0));
        assertEquals(java.util.List.of("Grace", "36"), document.rows().get(1));
    }

    @Test
    void handlesQuotedFieldsWithEmbeddedCommasAndEscapedQuotes() {
        var document = CsvParser.parse("name,note\n\"Lovelace, Ada\",\"She said \"\"hello\"\"\"\n");

        assertEquals("Lovelace, Ada", document.rows().getFirst().get(0));
        assertEquals("She said \"hello\"", document.rows().getFirst().get(1));
    }

    @Test
    void handlesNewlinesInsideQuotedFieldsAndCrlfLineEndings() {
        var document = CsvParser.parse("name,note\r\nAda,\"line one\nline two\"\r\nGrace,plain\r\n");

        assertEquals(2, document.rows().size());
        assertEquals("line one\nline two", document.rows().get(0).get(1));
        assertEquals("plain", document.rows().get(1).get(1));
    }

    @Test
    void skipsBlankLinesAndParsesAFileWithNoTrailingNewline() {
        var document = CsvParser.parse("name,age\nAda,24\n\nGrace,36");

        assertEquals(2, document.rows().size());
    }

    @Test
    void rejectsACompletelyEmptyFile() {
        assertThrows(ImportException.class, () -> CsvParser.parse(""));
    }
}

