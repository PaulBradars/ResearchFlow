package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import researchflow.app.*;
import researchflow.domain.*;
import researchflow.service.CancellationToken;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class P2ReliabilityTest {
    @TempDir Path directory;
    AppServices open() { return AppServices.initialize(new AppConfig(directory.resolve("live.db"), directory.resolve("logs"), false, false, "http://localhost:11434", "", 60)); }
    UUID study(AppServices app) { return app.studies().create("Recovery study", "", "", "", null, null, List.of()).id(); }
    void sql(AppServices app, String sql) throws Exception { try(var c = app.connections().open(); var s = c.createStatement()) { s.execute(sql); } }

    @Test void onlineBackupRestoreAndInvalidRecoveryPreserveData() throws Exception {
        var app = open(); var original = study(app);
        var backup = directory.resolve("backup.db"); app.recovery().backup(backup);
        var later = study(app);
        var safety = app.recovery().restore(backup);
        assertTrue(Files.size(safety) > 0);
        app = open();
        assertTrue(app.studies().find(original).isPresent()); assertTrue(app.studies().find(later).isEmpty());
        var invalid = directory.resolve("invalid.db"); Files.writeString(invalid, "not a database");
        var restored = app;
        assertThrows(PersistenceException.class, () -> restored.recovery().restore(invalid));
        assertTrue(app.studies().find(original).isPresent());
        new MigrationRunner(app.connections()).migrate();
        try(var c = app.connections().open(); var s = c.createStatement(); var r = s.executeQuery("SELECT COUNT(*) FROM schema_migrations")) { assertTrue(r.next()); assertEquals(6, r.getInt(1)); }
    }

    @Test void futureMigrationAndActiveFileAreRejected() throws Exception {
        var app = open(); var id = study(app); var backup = directory.resolve("future.db"); app.recovery().backup(backup);
        try(var c = new ConnectionFactory(backup).open(); var s = c.createStatement()) { s.execute("UPDATE schema_migrations SET version='V999' WHERE version=(SELECT MAX(version) FROM schema_migrations)"); }
        assertThrows(PersistenceException.class, () -> app.recovery().restore(backup));
        assertThrows(IllegalArgumentException.class, () -> app.recovery().backup(directory.resolve("live.db")));
        assertTrue(app.studies().find(id).isPresent());
    }

    @Test void olderBackupIsMigratedOnStageWithoutChangingSource() throws Exception {
        var app = open(); var id = study(app); var backup = directory.resolve("v005.db"); app.recovery().backup(backup);
        try(var c = new ConnectionFactory(backup).open(); var s = c.createStatement()) {
            s.execute("ALTER TABLE analyses DROP COLUMN variables_json");
            s.execute("ALTER TABLE analyses DROP COLUMN evidence_schema_version");
            s.execute("DELETE FROM schema_migrations WHERE version=(SELECT MAX(version) FROM schema_migrations)");
        }
        var hash = researchflow.util.AtomicFiles.sha256(backup);
        app.recovery().restore(backup);
        assertTrue(open().studies().find(id).isPresent());
        assertEquals(hash, researchflow.util.AtomicFiles.sha256(backup));
    }

    @Test void claimedCurrentDatabaseWithMissingSchemaIsRejected() throws Exception {
        var app = open(); var id = study(app); var backup = directory.resolve("broken-schema.db"); app.recovery().backup(backup);
        try(var c = new ConnectionFactory(backup).open(); var s = c.createStatement()) { s.execute("ALTER TABLE analyses DROP COLUMN variables_json"); }
        assertThrows(PersistenceException.class, () -> app.recovery().restore(backup));
        assertTrue(app.studies().find(id).isPresent());
    }

    @Test void cancellationBeforeTransactionCommitRollsBack() throws Exception {
        var app = open(); var id = study(app);
        try {
            assertThrows(java.util.concurrent.CancellationException.class, () -> new TransactionManager(app.connections()).inTransaction(c -> {
                try(var s = c.createStatement()) { s.execute("UPDATE studies SET title='Must roll back'"); }
                Thread.currentThread().interrupt(); return null;
            }));
        } finally { Thread.interrupted(); }
        assertEquals("Recovery study", app.studies().find(id).orElseThrow().title());
    }

    @Test void importReportsOperationalFailureAfterCommittedRows() throws Exception {
        var app = open(); var id = study(app); var csv = directory.resolve("import.csv"); Files.writeString(csv, "value\n1\n2\n3\n");
        sql(app, "CREATE TRIGGER fail_second BEFORE INSERT ON responses WHEN (SELECT COUNT(*) FROM responses)=1 BEGIN SELECT RAISE(ABORT,'injected failure'); END");
        var preview = app.imports().preview(csv);
        var result = app.imports().importInto(id, "Imported", preview, preview.columns());
        assertFalse(result.completedNormally()); assertTrue(result.setupSucceeded());
        assertEquals(3, result.totalRows()); assertEquals(1, result.importedCount()); assertEquals(1, result.failedCount()); assertEquals(1, result.unprocessedCount());
        assertEquals(1, app.responses().list(id).size());
        app.imports().exportErrors(result, directory.resolve("errors.txt")); assertTrue(Files.readString(directory.resolve("errors.txt")).contains("FAILED"));
    }

    @Test void importCancellationReturnsPartialReceipt() throws Exception {
        var app = open(); var id = study(app); var csv = directory.resolve("cancel.csv"); Files.writeString(csv, "value\n1\n2\n3\n");
        var preview = app.imports().preview(csv); var token = new CancellationToken();
        var result = app.imports().importInto(id, "Cancelled", preview, preview.columns(), token, count -> token.cancel());
        assertEquals(researchflow.dataimport.ImportResult.State.CANCELLED, result.state());
        assertEquals(1, result.importedCount()); assertEquals(2, result.unprocessedCount());
        assertEquals(1, open().responses().list(id).size());
    }

    @Test void reportDistinguishesAuditFailureAndPublicationFailure() throws Exception {
        var app = open(); var id = study(app);
        sql(app, "CREATE TRIGGER fail_report BEFORE INSERT ON audit_logs WHEN NEW.event_type='REPORT_GENERATED' BEGIN SELECT RAISE(ABORT,'audit unavailable'); END");
        var target = directory.resolve("report.html");
        var result = app.reports().exportDetailed(id, target);
        assertFalse(result.auditRecorded()); assertTrue(Files.readString(target).startsWith("<!doctype html>"));
        assertEquals(researchflow.util.AtomicFiles.sha256(target), result.sha256());
        assertThrows(PersistenceException.class, () -> app.reports().exportDetailed(id, directory.resolve("missing/report.html")));
        assertTrue(Files.isRegularFile(target));
    }

    @Test void desktopWorkflowAndAllEvidenceTypesSurviveRestart() throws Exception {
        var app = open(); var id = study(app);
        var form = app.forms().create(id, "Survey", "");
        var x = Question.create("x", "Original X", "", QuestionType.NUMBER, false, null, null, List.of());
        var y = Question.create("y", "Y", "", QuestionType.NUMBER, false, null, null, List.of());
        var g = Question.create("g", "Group", "", QuestionType.SINGLE_CHOICE, false, null, null, List.of(QuestionOption.create("A"), QuestionOption.create("B")));
        var h = Question.create("h", "Category", "", QuestionType.YES_NO, false, null, null, List.of());
        form = app.forms().updateStructure(form.id(), form.title(), form.description(), List.of(form.sections().getFirst().withQuestions(List.of(x,y,g,h))));
        form = app.forms().activate(form.id());
        for(int i=1;i<=6;i++) app.submissions().submit(form.id(), null, Map.of(x.id(), ""+i, y.id(), ""+(i*2), g.id(), i<=3 ? "A" : "B", h.id(), i%2==0 ? "yes" : "no"));
        var response = app.responses().list(id).getFirst();
        app.datasets().detail(id, response.id());
        app.corrections().correct(id, response.id(), x.id(), "7", "Verified source");
        var issues = app.quality().scan(id);
        if (!issues.isEmpty()) app.qualityReview().apply(new researchflow.command.ReviewCommand.Accept(issues.getFirst().id(), "Reviewed source"));
        var version = app.versions().createSnapshot(id, "Reviewed baseline", "");
        var evidence = new ArrayList<EvidenceBundle>();
        for (var method : AnalysisMethod.values()) {
            var primary = method == AnalysisMethod.FREQUENCY || method == AnalysisMethod.CROSS_TABULATION ? g.id() : x.id();
            var secondary = switch(method) { case CORRELATION -> y.id(); case CROSS_TABULATION -> h.id(); case GROUP_COMPARISON -> g.id(); default -> null; };
            evidence.add(app.analysis().run(id, new AnalysisPlan(method, primary, secondary,
                    List.of(new AnalysisFilter(x.id(), DatasetFilterOperator.GREATER_THAN, "0")), version.id()), "AI", new ModelMetadata("test-model", "test-runtime", "v1")));
        }
        var reopened = open();
        for (var original : evidence) {
            var history = reopened.analysis().reopen(id, original.id());
            assertTrue(history.supported(), history.notice()); assertEquals(original, history.evidence());
            assertEquals("AI", history.stored().source()); assertTrue(history.stored().modelMetadataJson().contains("test-model"));
            var values = reopened.analysis().chartValues(id, history.evidence());
            researchflow.visualization.ChartBuilder.build(history.evidence(), values.primary(), values.secondary());
            var finding = reopened.findings().draft(id, history.evidence(), "Reviewed historical evidence", null);
            reopened.findings().approve(finding.id());
        }
        reopened.corrections().correct(id, response.id(), x.id(), "8", "Later correction");
        assertTrue(reopened.analysis().provenance(id, evidence.getFirst().id()).contains("STALE"));
        reopened.versions().createSnapshot(id, "Later baseline", "");
        var export = reopened.reports().exportDetailed(id, directory.resolve("workflow.html")); assertTrue(export.auditRecorded());
        assertTrue(Files.readString(export.path()).contains("Historical version"));
        assertEquals(5, open().findings().list(id).size()); assertEquals(6, open().responses().list(id).size());
        assertTrue(Files.readString(export.path()).contains(version.id().toString()));
        sql(reopened, "UPDATE analyses SET evidence_schema_version=0, variables_json=NULL WHERE id='" + evidence.getFirst().id() + "'");
        var legacy = open().analysis().reopen(id, evidence.getFirst().id());
        assertTrue(legacy.supported()); assertTrue(legacy.notice().contains("Legacy"));
        sql(reopened, "UPDATE analyses SET result_json='{}' WHERE id='" + evidence.getFirst().id() + "'");
        assertFalse(open().analysis().reopen(id, evidence.getFirst().id()).supported());
    }
}

