package researchflow.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.function.Executable;
import researchflow.app.AppConfig;
import researchflow.app.AppServices;
import researchflow.command.ReviewCommand;
import researchflow.domain.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DataIntegrityRegressionTest {
    @TempDir Path directory;

    @Test
    void restoreRemovesLaterResponseFromEveryLiveReaderAndCanRestoreItAgain() throws Exception {
        var f = fixture();
        var original = submit(f, "10");
        var v1 = snapshot(f);
        var later = submit(f, "20");
        var v2 = snapshot(f);
        var originalSnapshot = snapshotRows(f, v1.id());
        var restored = f.app.versions().restore(f.study.id(), v1.id(), "Return to first population");

        assertEquals(originalSnapshot, snapshotRows(f, restored.id()));
        assertEquals(originalSnapshot, snapshotRows(f, v1.id()));
        assertEquals(1, f.app.datasets().query(f.study.id(), DatasetQuery.firstPage()).totalRows());
        assertThrows(IllegalArgumentException.class, () -> f.app.datasets().detail(f.study.id(), later.id()));
        assertEquals(List.of(original.id()), f.app.responses().list(f.study.id()).stream().map(ResponseSummary::id).toList());
        assertEquals(1, f.app.studies().metrics(f.study.id()).responses());
        assertEquals(1, f.app.reports().compose(f.study.id()).responseCount());
        assertEquals(2, scalar(f, "SELECT COUNT(*) FROM responses")); // Preserved, not deleted.
        assertThrows(IllegalArgumentException.class, () -> f.app.corrections().correct(
                f.study.id(), later.id(), f.number.id(), "30", "Not in restored population"));
        assertEquals(1, frequency(f, restored.id()).totalCount());

        var returned = f.app.versions().restore(f.study.id(), v2.id(), "Return to second population");
        assertEquals(snapshotRows(f, v2.id()), snapshotRows(f, returned.id()));
        assertEquals(2, f.app.responses().list(f.study.id()).size());
        assertEquals(DatasetFreshness.CURRENT, f.app.versions().freshness(f.study.id()));
    }

    @Test
    void restoreMakesLaterAddedAnswerMissingWithoutLosingItsCorrectionHistory() throws Exception {
        var f = fixture();
        var blank = f.app.submissions().submit(f.form.id(), null, Map.of());
        var v1 = snapshot(f);
        f.app.corrections().correct(f.study.id(), blank.id(), f.number.id(), "42", "Entered from source");
        var v2 = snapshot(f);
        // Include the decimal point so this cannot accidentally match a random response UUID.
        var search = new DatasetQuery(null, "42.0", null, null, "", null, null, 0, 50);
        assertEquals(1, f.app.datasets().query(f.study.id(), search).totalRows());
        var restored = f.app.versions().restore(f.study.id(), v1.id(), "Return to missing answer");

        assertEquals(snapshotRows(f, v1.id()), snapshotRows(f, restored.id()));
        assertFalse(f.app.datasets().detail(f.study.id(), blank.id()).cells().containsKey(f.number.id()));
        assertEquals(0, f.app.responses().list(f.study.id()).getFirst().answerCount());
        assertEquals(1, frequency(f, restored.id()).missingCount());
        assertEquals(0, f.app.datasets().query(f.study.id(), search).totalRows());
        var missing = new DatasetQuery(null, "", f.number.id(), DatasetFilterOperator.IS_MISSING, "", null, null, 0, 50);
        assertEquals(1, f.app.datasets().query(f.study.id(), missing).totalRows());
        assertEquals(1, scalar(f, "SELECT COUNT(*) FROM answer_corrections"));
        assertEquals(1, scalar(f, "SELECT COUNT(*) FROM answers WHERE is_present=0"));
        f.app.corrections().correct(f.study.id(), blank.id(), f.number.id(), "50", "Re-enter from source");
        assertEquals("null", text(f, "SELECT old_value_json FROM answer_corrections ORDER BY corrected_at DESC LIMIT 1"));
        f.app.versions().restore(f.study.id(), v2.id(), "Recover preserved later answer");
        assertEquals("42.0", f.app.datasets().detail(f.study.id(), blank.id()).cells().get(f.number.id()).displayValue());
    }

    @Test
    void blankResponseCountsInSnapshotPopulationMissingCountsAndWarnings() {
        var f = fixture();
        var blank = f.app.submissions().submit(f.form.id(), null, Map.of());
        submit(f, "10");
        var version = snapshot(f);
        var raw = new JdbcAnalysisRepository(f.app.connections(), new TransactionManager(f.app.connections())).loadVersionData(version.id());
        assertTrue(raw.stream().anyMatch(row -> row.responseId().equals(blank.id()) && row.answer() == null));
        var evidence = f.app.analysis().run(f.study.id(), plan(f, version.id()));
        var result = (AnalysisResult.Frequency) evidence.result();
        assertEquals(1, result.totalCount());
        assertEquals(1, result.missingCount());
        assertEquals(100, result.categories().getFirst().percentage());
        assertTrue(evidence.warnings().stream().anyMatch(w -> w.startsWith("50%")));
        var missingPlan = new AnalysisPlan(AnalysisMethod.FREQUENCY, f.number.id(), null,
                List.of(new AnalysisFilter(f.number.id(), DatasetFilterOperator.IS_MISSING, "")), version.id());
        assertEquals(1, ((AnalysisResult.Frequency) f.app.analysis().run(f.study.id(), missingPlan).result()).missingCount());
    }

    @Test
    void restorePreservesBlankResponseExclusionInBothDirections() throws Exception {
        var f = fixture();
        var blank = f.app.submissions().submit(f.form.id(), null, Map.of());
        var included = snapshot(f);
        execute(f, "UPDATE responses SET status='EXCLUDED' WHERE id=?", blank.id());
        var excluded = snapshot(f);
        f.app.versions().restore(f.study.id(), included.id(), "Include blank response");
        assertEquals("COMPLETE", text(f, "SELECT status FROM responses WHERE id=?", blank.id()));
        var restored = f.app.versions().restore(f.study.id(), excluded.id(), "Restore blank exclusion");
        assertEquals("EXCLUDED", text(f, "SELECT status FROM responses WHERE id=?", blank.id()));
        assertEquals(0, frequency(f, restored.id()).missingCount());
        assertEquals(snapshotRows(f, excluded.id()), snapshotRows(f, restored.id()));
    }

    @Test
    void emptyVersionRestoresAnEmptyDataset() {
        var f = fixture();
        var empty = snapshot(f);
        submit(f, "10");
        var restored = f.app.versions().restore(f.study.id(), empty.id(), "Return to empty baseline");
        assertTrue(f.app.responses().list(f.study.id()).isEmpty());
        assertEquals(0, frequency(f, restored.id()).totalCount());
        assertEquals(DatasetFreshness.CURRENT, f.app.versions().freshness(f.study.id()));
    }

    @Test
    void restoreFailureRollsBackMembershipAnswersActiveVersionAndAudit() throws Exception {
        var f = fixture();
        var first = submit(f, "10");
        var version = snapshot(f);
        f.app.corrections().correct(f.study.id(), first.id(), f.number.id(), "20", "Change for failure test");
        submit(f, "30");
        long audits = scalar(f, "SELECT COUNT(*) FROM audit_logs");
        execute(f, """
                CREATE TRIGGER fail_restore BEFORE INSERT ON version_response_snapshots
                BEGIN SELECT RAISE(ABORT, 'injected snapshot failure'); END
                """);
        assertThrows(PersistenceException.class, () -> f.app.versions().restore(f.study.id(), version.id(), "Injected failure"));
        assertEquals(2, f.app.responses().list(f.study.id()).size());
        assertEquals("20.0", f.app.datasets().detail(f.study.id(), first.id()).cells().get(f.number.id()).displayValue());
        assertEquals(version.id(), f.app.versions().active(f.study.id()).orElseThrow().id());
        assertEquals(1, f.app.versions().list(f.study.id()).size());
        assertEquals(audits, scalar(f, "SELECT COUNT(*) FROM audit_logs"));
    }

    @Test
    void restoreKeepsScientificNumbersAndLiteralBackslashesExact() {
        var f = fixture();
        var value = "literal \\n and \\r, quoted \"value\", actual\nnewline";
        var response = f.app.submissions().submit(f.form.id(), null, Map.of(f.number.id(), "0.00000001", f.label.id(), value));
        var version = snapshot(f);
        f.app.corrections().correct(f.study.id(), response.id(), f.number.id(), "2", "Temporary numeric change");
        f.app.corrections().correct(f.study.id(), response.id(), f.label.id(), "other", "Temporary text change");
        f.app.versions().restore(f.study.id(), version.id(), "Recover exact typed values");
        var cells = f.app.datasets().detail(f.study.id(), response.id()).cells();
        assertEquals("1.0E-8", cells.get(f.number.id()).displayValue());
        assertEquals(value, cells.get(f.label.id()).displayValue());
        assertEquals(DatasetFreshness.CURRENT, f.app.versions().freshness(f.study.id()));
    }

    @Test
    void freshnessTracksCorrectionsMembershipAndExclusionWithoutCreatingVersions() throws Exception {
        var f = fixture();
        var response = submit(f, "10");
        assertEquals(DatasetFreshness.NO_ACTIVE_VERSION, f.app.versions().freshness(f.study.id()));
        snapshot(f);
        assertEquals(DatasetFreshness.CURRENT, f.app.versions().freshness(f.study.id()));
        f.app.corrections().correct(f.study.id(), response.id(), f.number.id(), "20", "Correct source value");
        assertEquals(DatasetFreshness.STALE, f.app.versions().freshness(f.study.id()));
        var oldResult = f.app.analysis().run(f.study.id(), plan(f, null));
        assertEquals("10", ((AnalysisResult.Frequency) oldResult.result()).categories().getFirst().value());
        assertTrue(oldResult.warnings().stream().anyMatch(w -> w.contains("Live data differs")));
        assertEquals(1, f.app.versions().list(f.study.id()).size());
        snapshot(f);
        assertEquals(DatasetFreshness.CURRENT, f.app.versions().freshness(f.study.id()));
        f.app.submissions().submit(f.form.id(), null, Map.of());
        assertEquals(DatasetFreshness.STALE, f.app.versions().freshness(f.study.id()));
        snapshot(f);
        execute(f, "UPDATE responses SET status='EXCLUDED' WHERE id=?", response.id());
        assertEquals(DatasetFreshness.STALE, f.app.versions().freshness(f.study.id()));
        snapshot(f);
        assertEquals(DatasetFreshness.CURRENT, f.app.versions().freshness(f.study.id()));
    }

    @Test
    void approvedFindingEditsPreserveRevisionAndRequireReapprovalForReports() throws Exception {
        var f = fixture();
        submit(f, "10");
        var evidence = f.app.analysis().run(f.study.id(), plan(f, null));
        var draft = f.app.findings().draft(f.study.id(), evidence, "Original reviewed finding.", null);
        var approved = f.app.findings().approve(draft.id());
        assertEquals(1, f.app.reports().compose(f.study.id()).approvedFindings().size());
        var edited = f.app.findings().edit(draft.id(), "Revised wording requires review.");
        assertEquals(FindingStatus.DRAFT, edited.status());
        assertNull(edited.approvedAt());
        assertEquals(evidence.id(), edited.analysisId());
        assertEquals(evidence.datasetVersionId(), edited.datasetVersionId());
        assertTrue(f.app.reports().compose(f.study.id()).approvedFindings().isEmpty());
        assertEquals(approved.text(), text(f, "SELECT previous_text FROM finding_text_revisions WHERE finding_id=?", draft.id()));
        assertEquals(approved.approvedAt().toString(), text(f, "SELECT previous_approved_at FROM finding_text_revisions WHERE finding_id=?", draft.id()));
        f.app.findings().approve(draft.id());
        assertEquals(edited.text(), f.app.reports().compose(f.study.id()).approvedFindings().getFirst().text());
    }

    @Test
    void unchangedFindingSaveKeepsApprovalAndRejectionClearsApprovalTimestamp() throws Exception {
        var f = fixture();
        submit(f, "10");
        var evidence = f.app.analysis().run(f.study.id(), plan(f, null));
        var draft = f.app.findings().draft(f.study.id(), evidence, "Unchanged approved statement.", null);
        var approved = f.app.findings().approve(draft.id());
        var unchanged = f.app.findings().edit(draft.id(), "  " + draft.text() + "  ");
        assertEquals(approved, unchanged);
        assertEquals(0, scalar(f, "SELECT COUNT(*) FROM finding_text_revisions"));
        assertNull(f.app.findings().reject(draft.id()).approvedAt());
    }

    @Test
    void findingEditAuditFailureRollsBackTextRevisionAndApproval() throws Exception {
        var f = fixture();
        submit(f, "10");
        var draft = f.app.findings().draft(f.study.id(), f.app.analysis().run(f.study.id(), plan(f, null)), "Original wording to approve.", null);
        var approved = f.app.findings().approve(draft.id());
        execute(f, """
                CREATE TRIGGER fail_finding_audit BEFORE INSERT ON audit_logs
                WHEN NEW.event_type='FINDING_UPDATED' BEGIN SELECT RAISE(ABORT, 'audit failure'); END
                """);
        assertThrows(PersistenceException.class, () -> f.app.findings().edit(draft.id(), "Changed but uncommitted wording."));
        assertEquals(approved, f.app.findings().list(f.study.id()).getFirst());
        assertEquals(0, scalar(f, "SELECT COUNT(*) FROM finding_text_revisions"));
    }

    @Test
    void qualityResolutionFailureRollsBackAnswerHistoryIssueAndBothAudits() throws Exception {
        var f = fixture();
        var issue = rangeIssue(f);
        var audits = scalar(f, "SELECT COUNT(*) FROM audit_logs");
        execute(f, """
                CREATE TRIGGER fail_resolution BEFORE UPDATE OF status ON quality_issues
                WHEN NEW.status='RESOLVED' BEGIN SELECT RAISE(ABORT, 'injected resolution failure'); END
                """);
        assertThrows(PersistenceException.class, () -> f.app.qualityReview().apply(correction(f, issue)));
        assertEquals(150, scalar(f, "SELECT value_number FROM answers WHERE response_id=? AND question_id=?", issue.responseId(), f.number.id()));
        assertEquals(0, scalar(f, "SELECT COUNT(*) FROM answer_corrections"));
        assertEquals("OPEN", text(f, "SELECT status FROM quality_issues WHERE id=?", issue.id()));
        assertEquals(audits, scalar(f, "SELECT COUNT(*) FROM audit_logs"));
    }

    @Test
    void successfulQualityCorrectionCommitsAnswerHistoryResolutionAndBothAudits() throws Exception {
        var f = fixture();
        var issue = rangeIssue(f);
        var resolved = f.app.qualityReview().apply(correction(f, issue));
        assertEquals(QualityIssueStatus.RESOLVED, resolved.status());
        assertEquals(25, scalar(f, "SELECT value_number FROM answers WHERE response_id=? AND question_id=?", issue.responseId(), f.number.id()));
        assertEquals(1, scalar(f, "SELECT COUNT(*) FROM answer_corrections"));
        assertEquals(1, scalar(f, "SELECT COUNT(*) FROM audit_logs WHERE event_type='ANSWER_CORRECTED'"));
        assertEquals(1, scalar(f, "SELECT COUNT(*) FROM audit_logs WHERE event_type='QUALITY_ISSUE_RESOLVED'"));
    }

    @Test
    void finalQualityAuditFailureAlsoRollsBackTheWholeCorrection() throws Exception {
        var f = fixture();
        var issue = rangeIssue(f);
        var audits = scalar(f, "SELECT COUNT(*) FROM audit_logs");
        execute(f, """
                CREATE TRIGGER fail_resolution_audit BEFORE INSERT ON audit_logs
                WHEN NEW.event_type='QUALITY_ISSUE_RESOLVED' BEGIN SELECT RAISE(ABORT, 'injected audit failure'); END
                """);
        assertThrows(PersistenceException.class, () -> f.app.qualityReview().apply(correction(f, issue)));
        assertEquals(150, scalar(f, "SELECT value_number FROM answers WHERE response_id=?", issue.responseId()));
        assertEquals("OPEN", text(f, "SELECT status FROM quality_issues WHERE id=?", issue.id()));
        assertEquals(0, scalar(f, "SELECT COUNT(*) FROM answer_corrections"));
        assertEquals(audits, scalar(f, "SELECT COUNT(*) FROM audit_logs"));
    }

    @Test
    void restoringAnotherStudysVersionIsRejectedWithoutChangingEitherDataset() throws Exception {
        var f = fixture();
        submit(f, "10");
        var version = snapshot(f);
        var other = f.app.studies().create("Other study", "", "", "", null, null, List.of());
        var otherVersion = f.app.versions().createSnapshot(other.id(), "Other baseline", "");
        var before = databaseCounts(f);
        assertThrows(IllegalArgumentException.class, () -> f.app.versions().restore(f.study.id(), otherVersion.id(), "Wrong study target"));
        assertEquals(before, databaseCounts(f));
        assertEquals(version.id(), f.app.versions().active(f.study.id()).orElseThrow().id());
        assertEquals(otherVersion.id(), f.app.versions().active(other.id()).orElseThrow().id());
    }

    @Test
    void mismatchedReviewTargetsRejectWithoutAnyMutation() throws Exception {
        var f = fixture();
        var issue = rangeIssue(f);
        var other = submit(f, "30");
        var audits = scalar(f, "SELECT COUNT(*) FROM audit_logs");
        var invalid = List.<ReviewCommand>of(
                new ReviewCommand.Correct(issue.id(), f.study.id(), other.id(), f.number.id(), "25", "Wrong response"),
                new ReviewCommand.Correct(issue.id(), f.study.id(), issue.responseId(), f.label.id(), "changed", "Wrong question"),
                new ReviewCommand.Correct(issue.id(), UUID.randomUUID(), issue.responseId(), f.number.id(), "25", "Wrong study"),
                new ReviewCommand.Exclude(issue.id(), f.study.id(), other.id(), "Wrong response"),
                new ReviewCommand.Exclude(issue.id(), UUID.randomUUID(), issue.responseId(), "Wrong study"));
        for (var command : invalid) assertThrows(IllegalArgumentException.class, () -> f.app.qualityReview().apply(command));
        assertEquals(150, scalar(f, "SELECT value_number FROM answers WHERE response_id=?", issue.responseId()));
        assertEquals(30, scalar(f, "SELECT value_number FROM answers WHERE response_id=?", other.id()));
        assertEquals(0, scalar(f, "SELECT COUNT(*) FROM answer_corrections"));
        assertEquals("OPEN", text(f, "SELECT status FROM quality_issues WHERE id=?", issue.id()));
        assertEquals(audits, scalar(f, "SELECT COUNT(*) FROM audit_logs"));
    }

    @Test
    void responseLevelIssueCannotBeCorrectedAgainstAnArbitraryQuestion() throws Exception {
        var f = fixture();
        submit(f, "10"); submit(f, "10");
        var duplicate = f.app.quality().scan(f.study.id()).stream()
                .filter(q -> q.type() == QualityIssueType.DUPLICATE_RESPONSE).findFirst().orElseThrow();
        var audits = scalar(f, "SELECT COUNT(*) FROM audit_logs");
        assertThrows(IllegalArgumentException.class, () -> f.app.qualityReview().apply(
                new ReviewCommand.Correct(duplicate.id(), f.study.id(), duplicate.responseId(), f.number.id(), "20", "No question target")));
        assertEquals(audits, scalar(f, "SELECT COUNT(*) FROM audit_logs"));
    }

    @Test
    void archivedStudyRejectsEveryServiceMutationAndKeepsReadOperationsAvailable() throws Exception {
        var f = fixture();
        var issue = rangeIssue(f);
        var draftForm = f.app.forms().create(f.study.id(), "Still draft", "");
        var version = snapshot(f);
        var evidence = f.app.analysis().run(f.study.id(), plan(f, version.id()));
        var finding = f.app.findings().draft(f.study.id(), evidence, "Finding pending researcher review.", null);
        var csv = directory.resolve("import.csv"); Files.writeString(csv, "value\n20\n");
        var preview = f.app.imports().preview(csv);
        f.app.studies().archive(f.study.id());
        var before = databaseCounts(f);
        var operations = new LinkedHashMap<String, Executable>();
        operations.put("study edit", () -> f.app.studies().update(f.study.id(), "Changed", "", "", "", null, null, List.of()));
        operations.put("form create", () -> f.app.forms().create(f.study.id(), "New form", ""));
        operations.put("form edit", () -> f.app.forms().updateStructure(draftForm.id(), "Changed", "", draftForm.sections()));
        operations.put("activate", () -> f.app.forms().activate(draftForm.id()));
        operations.put("close", () -> f.app.forms().close(f.form.id()));
        operations.put("submit", () -> submit(f, "20"));
        operations.put("import", () -> f.app.imports().importInto(f.study.id(), "Import", preview, preview.columns()));
        operations.put("correct", () -> f.app.corrections().correct(f.study.id(), issue.responseId(), f.number.id(), "25", "Correction reason"));
        operations.put("quality scan", () -> f.app.quality().scan(f.study.id()));
        operations.put("quality correct", () -> f.app.qualityReview().apply(correction(f, issue)));
        operations.put("quality accept", () -> f.app.qualityReview().apply(new ReviewCommand.Accept(issue.id(), "Accept issue")));
        operations.put("quality defer", () -> f.app.qualityReview().apply(new ReviewCommand.Defer(issue.id(), "Defer issue")));
        operations.put("quality exclude", () -> f.app.qualityReview().apply(new ReviewCommand.Exclude(issue.id(), f.study.id(), issue.responseId(), "Exclude response")));
        operations.put("snapshot", () -> snapshot(f));
        operations.put("restore", () -> f.app.versions().restore(f.study.id(), version.id(), "Restore reason"));
        operations.put("finding draft", () -> f.app.findings().draft(f.study.id(), evidence, "Another finding statement.", null));
        operations.put("finding edit", () -> f.app.findings().edit(finding.id(), "Edited finding statement."));
        operations.put("finding approve", () -> f.app.findings().approve(finding.id()));
        operations.put("finding reject", () -> f.app.findings().reject(finding.id()));
        operations.put("analysis", () -> f.app.analysis().run(f.study.id(), plan(f, version.id())));
        operations.put("AI question", () -> f.app.aiFacade().ask(f.study.id(), "What is the distribution?"));
        operations.put("audit", () -> f.app.audits().record(f.study.id(), "TEST", "STUDY", f.study.id(), "{}"));
        operations.put("report export", () -> f.app.reports().export(f.study.id(), directory.resolve("archived.html")));
        assertAll(operations.entrySet().stream().map(entry -> (Executable) () -> {
            var failure = assertThrows(IllegalStateException.class, entry.getValue(), entry.getKey());
            assertEquals("Archived studies are read-only.", failure.getMessage(), entry.getKey());
        }));
        assertEquals(before, databaseCounts(f));
        assertFalse(Files.exists(directory.resolve("archived.html")));
        assertEquals(2, f.app.forms().list(f.study.id()).size());
        assertEquals(1, f.app.responses().list(f.study.id()).size());
        assertEquals(1, f.app.datasets().query(f.study.id(), DatasetQuery.firstPage()).totalRows());
        assertFalse(f.app.quality().list(f.study.id(), null).isEmpty());
        assertEquals(1, f.app.versions().list(f.study.id()).size());
        assertEquals(DatasetFreshness.CURRENT, f.app.versions().freshness(f.study.id()));
        assertEquals(evidence.id(), f.app.analysis().details(evidence.id()).id());
        assertEquals(1, f.app.findings().list(f.study.id()).size());
        assertEquals(1, f.app.reports().compose(f.study.id()).responseCount());
        assertFalse(f.app.audits().timeline(f.study.id()).isEmpty());
    }

    private Fixture fixture() {
        var app = AppServices.initialize(new AppConfig(directory.resolve("test.db"), directory.resolve("logs"), false,
                false, "http://localhost:11434", "", 60));
        var study = app.studies().create("Integrity study", "", "", "", null, null, List.of());
        var form = app.forms().create(study.id(), "Optional survey", "");
        var number = Question.create("value", "Value", "", QuestionType.NUMBER, false, 0d, 100d, List.of());
        var label = Question.create("label", "Label", "", QuestionType.SHORT_TEXT, false, null, null, List.of());
        form = app.forms().updateStructure(form.id(), form.title(), "", List.of(form.sections().getFirst().withQuestions(List.of(number, label))));
        form = app.forms().activate(form.id());
        return new Fixture(app, study, form, number, label);
    }

    private static Response submit(Fixture f, String value) { return f.app.submissions().submit(f.form.id(), null, Map.of(f.number.id(), value)); }
    private static DatasetVersion snapshot(Fixture f) { return f.app.versions().createSnapshot(f.study.id(), "Snapshot baseline", ""); }
    private static AnalysisPlan plan(Fixture f, UUID version) { return new AnalysisPlan(AnalysisMethod.FREQUENCY, f.number.id(), null, List.of(), version); }
    private static AnalysisResult.Frequency frequency(Fixture f, UUID version) { return (AnalysisResult.Frequency) f.app.analysis().run(f.study.id(), plan(f, version)).result(); }

    private static QualityIssue rangeIssue(Fixture f) throws Exception {
        var response = submit(f, "10");
        execute(f, "UPDATE answers SET value_number=150 WHERE response_id=? AND question_id=?", response.id(), f.number.id());
        return f.app.quality().scan(f.study.id()).stream().filter(q -> q.type() == QualityIssueType.INVALID_RANGE).findFirst().orElseThrow();
    }

    private static ReviewCommand.Correct correction(Fixture f, QualityIssue issue) {
        return new ReviewCommand.Correct(issue.id(), f.study.id(), issue.responseId(), f.number.id(), "25", "Corrected from source");
    }

    private static void execute(Fixture f, String sql, Object... args) throws Exception {
        try (var connection = f.app.connections().open(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setString(i + 1, args[i].toString());
            statement.executeUpdate();
        }
    }

    private static String text(Fixture f, String sql, Object... args) throws Exception {
        try (var connection = f.app.connections().open(); var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setString(i + 1, args[i].toString());
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getString(1); }
        }
    }

    private static long scalar(Fixture f, String sql, Object... args) throws Exception { return (long) Double.parseDouble(text(f, sql, args)); }

    private static List<String> snapshotRows(Fixture f, UUID version) throws Exception {
        var values = new ArrayList<String>();
        try (var connection = f.app.connections().open()) {
            for (var sql : List.of(
                    "SELECT 'r:' || response_id || ':' || excluded FROM version_response_snapshots WHERE dataset_version_id=?",
                    "SELECT 'a:' || response_id || ':' || question_id || ':' || value_json FROM version_answer_snapshots WHERE dataset_version_id=?")) {
                try (var statement = connection.prepareStatement(sql)) {
                    statement.setString(1, version.toString());
                    try (var rows = statement.executeQuery()) { while (rows.next()) values.add(rows.getString(1)); }
                }
            }
        }
        return values.stream().sorted().toList();
    }

    private static Map<String, Long> databaseCounts(Fixture f) throws Exception {
        var result = new LinkedHashMap<String, Long>();
        for (var table : List.of("forms", "responses", "answers", "answer_corrections", "quality_issues", "dataset_versions",
                "version_response_snapshots", "version_answer_snapshots", "findings", "finding_text_revisions", "analyses", "audit_logs", "chat_references")) {
            result.put(table, scalar(f, "SELECT COUNT(*) FROM " + table));
        }
        return result;
    }

    private record Fixture(AppServices app, Study study, Form form, Question number, Question label) { }
}
