package researchflow.dataimport;

import org.junit.jupiter.api.Test;
import researchflow.domain.QuestionType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportPlannerTest {
    @Test
    void proposesAnIncludedNotRequiredColumnPerHeaderWithInferredTypes() {
        var document = new CsvDocument(List.of("name", "age", "joined"),
                List.of(List.of("Ada", "24", "2026-01-01"), List.of("Grace", "36", "2026-02-14")));

        var columns = ImportPlanner.planColumns(document);

        assertEquals(3, columns.size());
        assertEquals(QuestionType.SHORT_TEXT, columns.get(0).type());
        assertEquals(QuestionType.NUMBER, columns.get(1).type());
        assertEquals(QuestionType.DATE, columns.get(2).type());
        assertTrue(columns.stream().allMatch(ImportColumnPlan::included));
        assertFalse(columns.stream().anyMatch(ImportColumnPlan::required));
        assertEquals("name", columns.get(0).variableKey());
    }

    @Test
    void namesAColumnWithABlankHeaderByItsPosition() {
        var document = new CsvDocument(List.of("name", ""), List.of(List.of("Ada", "x")));

        var columns = ImportPlanner.planColumns(document);

        assertEquals("Column 2", columns.get(1).label());
        assertEquals("column_2", columns.get(1).variableKey());
    }
}
