package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.dataimport.ImportColumnPlan;
import researchflow.domain.DatasetQuery;
import researchflow.domain.FormStatus;
import researchflow.domain.QuestionType;
import researchflow.service.DatasetImportService;
import researchflow.service.DatasetService;
import researchflow.service.FormService;
import researchflow.service.ResponseSubmissionService;
import researchflow.service.StudyService;
import researchflow.service.ValidationException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetImportServiceTest {
    @TempDir Path temporaryDirectory;

    @Test
    void importsValidRowsSkipsInvalidOnesAndTheResultIsQueryableThroughTheDatasetWorkspace() throws Exception {
        var fixture = fixture();
        var csv = temporaryDirectory.resolve("respondents.csv");
        Files.writeString(csv, """
                name,age,note
                Ada,24,Hello
                Grace,36,
                ,45,Bad row missing name
                Oscar,not-a-number,Text
                """, StandardCharsets.UTF_8);

        var preview = fixture.imports().preview(csv);
        assertEquals(3, preview.columns().size());
        assertEquals(QuestionType.SHORT_TEXT, preview.columns().get(0).type());
        // One value ("not-a-number") keeps every-value-parses inference conservative here; the researcher
        // reviewing the preview can still choose NUMBER deliberately, accepting that row will be skipped.
        assertEquals(QuestionType.SHORT_TEXT, preview.columns().get(1).type());

        var columns = List.of(
                withRequired(preview.columns().get(0), true),
                withType(preview.columns().get(1), QuestionType.NUMBER),
                preview.columns().get(2));

        var result = fixture.imports().importInto(fixture.studyId(), "Imported respondents", preview, columns);

        assertEquals(2, result.importedCount());
        assertEquals(2, result.skippedCount());
        assertEquals(2, result.errors().size());
        assertTrue(result.errors().stream().anyMatch(error -> error.rowNumber() == 4)); // missing required name
        assertTrue(result.errors().stream().anyMatch(error -> error.rowNumber() == 5)); // non-numeric age

        var form = fixture.formService().require(result.formId());
        assertEquals(FormStatus.ACTIVE, form.status());
        assertEquals(3, form.questions().size());

        var page = fixture.datasets().query(fixture.studyId(), DatasetQuery.firstPage());
        assertEquals(2, page.totalRows());
        assertTrue(page.rows().stream().anyMatch(row -> row.cell(form.questions().get(0).id()).displayValue().equals("Ada")));
    }

    @Test
    void rejectsAnImportWithNoIncludedColumns() throws Exception {
        var fixture = fixture();
        var csv = temporaryDirectory.resolve("data.csv");
        Files.writeString(csv, "a,b\n1,2\n", StandardCharsets.UTF_8);
        var preview = fixture.imports().preview(csv);
        var excluded = preview.columns().stream().map(column -> withIncluded(column, false)).toList();

        assertThrows(ValidationException.class,
                () -> fixture.imports().importInto(fixture.studyId(), "Title", preview, excluded));
    }

    private static ImportColumnPlan withRequired(ImportColumnPlan plan, boolean required) {
        return new ImportColumnPlan(plan.columnIndex(), plan.header(), plan.variableKey(), plan.label(),
                plan.type(), required, plan.included());
    }

    private static ImportColumnPlan withIncluded(ImportColumnPlan plan, boolean included) {
        return new ImportColumnPlan(plan.columnIndex(), plan.header(), plan.variableKey(), plan.label(),
                plan.type(), plan.required(), included);
    }

    private static ImportColumnPlan withType(ImportColumnPlan plan, QuestionType type) {
        return new ImportColumnPlan(plan.columnIndex(), plan.header(), plan.variableKey(), plan.label(),
                type, plan.required(), plan.included());
    }

    private Fixture fixture() {
        var connections = TestDatabase.migrated(temporaryDirectory);
        var transactions = new TransactionManager(connections);
        var study = new StudyService(new JdbcStudyRepository(connections, transactions))
                .create("Import Study", "", "", "", null, null, List.of());
        var formRepository = new JdbcFormRepository(connections, transactions);
        var formService = new FormService(formRepository);
        var responseRepository = new JdbcResponseRepository(connections, transactions);
        var submissions = new ResponseSubmissionService(formRepository, responseRepository);
        var imports = new DatasetImportService(formService, submissions);
        var datasetRepository = new JdbcDatasetRepository(connections, transactions);
        var datasets = new DatasetService(datasetRepository, formRepository);
        return new Fixture(study.id(), imports, formService, datasets);
    }

    private record Fixture(java.util.UUID studyId, DatasetImportService imports, FormService formService,
                           DatasetService datasets) { }
}
