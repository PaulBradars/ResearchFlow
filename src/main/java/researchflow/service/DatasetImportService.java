package researchflow.service;

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
 * Imports an external dataset file into a Study as a new Form and its Responses, reusing the existing
 * collection pipeline end to end: {@link FormService} creates and activates the Form exactly as the
 * Form workspace would, and {@link ResponseSubmissionService} validates and persists each row exactly
 * as a respondent submission would. Once imported, the data is ordinary Form/Response data — Dataset,
 * Quality review, versioning, and Analysis all work on it with no changes of their own.
 *
 * <p>A bad row (a value that fails typed validation) is skipped and reported, not fatal to the whole
 * file — external data is often messy. A bad file (unreadable, empty, no included columns) is fatal.
 */
public final class DatasetImportService {

    private final FormService forms;
    private final ResponseSubmissionService submissions;

    public DatasetImportService(FormService forms, ResponseSubmissionService submissions) {
        this.forms = forms;
        this.submissions = submissions;
    }

    /** Parses the file and proposes a column plan (type-inferred, all included, none required) for review. */
    public ImportPreview preview(Path csvFile) {
        return preview(csvFile, null);
    }

    public List<String> worksheets(Path file) { return researchflow.dataimport.DatasetFileReader.sheets(file); }

    public ImportPreview preview(Path file, String worksheet) {
        var document = researchflow.dataimport.DatasetFileReader.read(file, worksheet);
        return new ImportPreview(document, ImportPlanner.planColumns(document));
    }

    /** Creates the Form from {@code columns} (as edited by the researcher) and imports every data row. */
    public ImportResult importInto(UUID studyId, String formTitle, ImportPreview preview, List<ImportColumnPlan> columns) {
        return importInto(studyId, formTitle, preview, columns, new CancellationToken(), ignored -> { });
    }

    public ImportResult importInto(UUID studyId, String formTitle, ImportPreview preview, List<ImportColumnPlan> columns,
                                   CancellationToken token, java.util.function.IntConsumer progress) {
        forms.requireWritableStudy(studyId);
        var included = columns.stream().filter(ImportColumnPlan::included).toList();
        if (included.isEmpty()) throw new ValidationException(Map.of("columns", "Include at least one column."));
        var normalizedTitle = formTitle == null ? "" : formTitle.strip();
        if (normalizedTitle.isBlank()) throw new ValidationException(Map.of("title", "A form title is required."));

        var rows = preview.document().rows();
        UUID formId = null;
        boolean setupSucceeded = false;
        int imported = 0, skipped = 0, failed = 0;
        var errors = new ArrayList<ImportRowError>();
        try {
            token.check();
            var form = forms.create(studyId, normalizedTitle, "Imported from an external dataset file.");
            formId = form.id();
            var questions = included.stream().map(column -> Question.create(column.variableKey(), column.label(), "",
                    column.type(), column.required(), null, null, List.of())).toList();
            form = forms.updateStructure(form.id(), form.title(), form.description(),
                    List.of(form.sections().getFirst().withQuestions(questions)));
            token.check();
            form = forms.activate(form.id());
            setupSucceeded = true;
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                token.check();
                var row = rows.get(rowIndex);
                if (row.size() > preview.document().headers().size()) {
                    skipped++;
                    errors.add(new ImportRowError(rowIndex + 2, "More values than header columns; row was not imported."));
                    progress.accept(imported + skipped);
                    continue;
                }
                var rawAnswers = new LinkedHashMap<UUID, String>();
                for (int index = 0; index < included.size(); index++) {
                    int column = included.get(index).columnIndex();
                    rawAnswers.put(questions.get(index).id(), column < row.size() ? row.get(column) : "");
                }
                try {
                    submissions.submit(form.id(), null, rawAnswers);
                    imported++;
                } catch (ValidationException | IllegalArgumentException invalid) {
                    skipped++;
                    errors.add(new ImportRowError(rowIndex + 2, describe(invalid)));
                } catch (java.util.concurrent.CancellationException cancelled) { throw cancelled;
                } catch (RuntimeException failure) {
                    failed++;
                    errors.add(new ImportRowError(rowIndex + 2, describe(failure)));
                    return new ImportResult(formId, rows.size(), imported, skipped, failed, true, ImportResult.State.FAILED,
                            "Import stopped after an operational failure. Committed rows were preserved.", errors);
                }
                progress.accept(imported + skipped);
            }
            return new ImportResult(formId, rows.size(), imported, skipped, failed, true, ImportResult.State.COMPLETE, "", errors);
        } catch (java.util.concurrent.CancellationException cancelled) {
            return new ImportResult(formId, rows.size(), imported, skipped, failed, setupSucceeded, ImportResult.State.CANCELLED,
                    "Committed rows were preserved; remaining rows were not attempted.", errors);
        } catch (RuntimeException failure) {
            return new ImportResult(formId, rows.size(), imported, skipped, failed, setupSucceeded, ImportResult.State.FAILED,
                    "Import stopped: " + describe(failure) + ". Any created form and committed rows were preserved.", errors);
        }
    }

    public void exportErrors(ImportResult result, Path destination) {
        Path staged = null;
        try {
            staged = researchflow.util.AtomicFiles.stage(destination);
            var text = new StringBuilder(result.summary()).append("\n");
            for (var error : result.errors()) text.append("Row ").append(error.rowNumber()).append(": ").append(error.message()).append('\n');
            Files.writeString(staged, text, StandardCharsets.UTF_8);
            researchflow.util.AtomicFiles.publish(staged, destination);
        } catch (IOException failure) { throw new ImportException("Could not export import errors.", failure);
        } finally { researchflow.util.AtomicFiles.discard(staged); }
    }

    private static String describe(RuntimeException exception) {
        if (exception instanceof ValidationException validation) {
            return String.join(" ", validation.errors().values());
        }
        var message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

}
