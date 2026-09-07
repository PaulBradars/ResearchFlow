package researchflow.service;

import researchflow.dataimport.CsvParser;
import researchflow.dataimport.ImportColumnPlan;
import researchflow.dataimport.ImportException;
import researchflow.dataimport.ImportPlanner;
import researchflow.dataimport.ImportPreview;
import researchflow.dataimport.ImportResult;
import researchflow.dataimport.ImportRowError;
import researchflow.domain.Question;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Imports an external CSV file into a Study as a new Form and its Responses, reusing the existing
 * collection pipeline end to end: {@link FormService} creates and activates the Form exactly as the
 * Form workspace would, and {@link ResponseSubmissionService} validates and persists each row exactly
 * as a respondent submission would. Once imported, the data is ordinary Form/Response data — Dataset,
 * Quality review, versioning, and Analysis all work on it with no changes of their own.
 *
 * <p>A bad row (a value that fails typed validation) is skipped and reported, not fatal to the whole
 * file — external data is often messy. A bad file (unreadable, empty, no included columns) is fatal.
 */
public final class DatasetImportService {
    private static final int MAX_REPORTED_ERRORS = 25;

    private final FormService forms;
    private final ResponseSubmissionService submissions;

    public DatasetImportService(FormService forms, ResponseSubmissionService submissions) {
        this.forms = forms;
        this.submissions = submissions;
    }

    /** Parses the file and proposes a column plan (type-inferred, all included, none required) for review. */
    public ImportPreview preview(Path csvFile) {
        var document = CsvParser.parse(readFile(csvFile));
        return new ImportPreview(document, ImportPlanner.planColumns(document));
    }

    /** Creates the Form from {@code columns} (as edited by the researcher) and imports every data row. */
    public ImportResult importInto(UUID studyId, String formTitle, ImportPreview preview, List<ImportColumnPlan> columns) {
        var included = columns.stream().filter(ImportColumnPlan::included).toList();
        if (included.isEmpty()) throw new ValidationException(Map.of("columns", "Include at least one column."));
        var normalizedTitle = formTitle == null ? "" : formTitle.strip();
        if (normalizedTitle.isBlank()) throw new ValidationException(Map.of("title", "A form title is required."));

        var form = forms.create(studyId, normalizedTitle, "Imported from a CSV file.");
        var questions = included.stream()
                .map(column -> Question.create(column.variableKey(), column.label(), "", column.type(),
                        column.required(), null, null, List.of()))
                .toList();
        form = forms.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(questions)));
        form = forms.activate(form.id());

        var questionIdByColumn = new LinkedHashMap<Integer, UUID>();
        for (int index = 0; index < included.size(); index++) {
            questionIdByColumn.put(included.get(index).columnIndex(), questions.get(index).id());
        }

        var importedCount = 0;
        var errors = new ArrayList<ImportRowError>();
        var rows = preview.document().rows();
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            var row = rows.get(rowIndex);
            var rowNumber = rowIndex + 2; // header occupies row 1
            var rawAnswers = new LinkedHashMap<UUID, String>();
            for (var entry : questionIdByColumn.entrySet()) {
                rawAnswers.put(entry.getValue(), entry.getKey() < row.size() ? row.get(entry.getKey()) : "");
            }
            try {
                // No real "time started answering" exists for a bulk-imported historical row; leaving
                // startedAt null keeps duration_seconds null too, so FastSubmissionHandler correctly
                // has nothing to flag instead of a fabricated near-zero duration for every row.
                submissions.submit(form.id(), null, rawAnswers);
                importedCount++;
            } catch (ValidationException | IllegalArgumentException exception) {
                if (errors.size() < MAX_REPORTED_ERRORS) errors.add(new ImportRowError(rowNumber, describe(exception)));
            }
        }
        return new ImportResult(form.id(), importedCount, rows.size() - importedCount, errors);
    }

    private static String describe(RuntimeException exception) {
        if (exception instanceof ValidationException validation) {
            return String.join(" ", validation.errors().values());
        }
        var message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static String readFile(Path csvFile) {
        try {
            return Files.readString(csvFile, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ImportException("Could not read the file: " + exception.getMessage(), exception);
        }
    }
}
