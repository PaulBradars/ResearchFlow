package researchflow.persistence;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import researchflow.domain.*;
import researchflow.service.*;
import researchflow.ui.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises actual JavaFX controls; Windows CI/desktop runs have a graphical toolkit. */
@EnabledOnOs(OS.WINDOWS)
class QualityIssuesInteractionTest {
    @TempDir Path directory;

    @BeforeAll static void toolkit() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        fx(() -> { Platform.setImplicitExit(false); return null; });
    }

    @Test void selectingRowsImmediatelyShowsDetailsAndOnlyEligibleActions() throws Exception {
        var connections = TestDatabase.migrated(directory);
        var transactions = new TransactionManager(connections);
        var studies = new JdbcStudyRepository(connections, transactions);
        var study = new StudyService(studies).create("Quality interaction", "", "", "", null, null, List.of());
        var quality = new QualityService(new JdbcFormRepository(connections, transactions),
                new JdbcResponseRepository(connections, transactions), new JdbcQualityRepository(connections, transactions),
                new StudyWriteGuard(studies));
        new JdbcQualityRepository(connections, transactions).reconcile(study.id(), List.of(
                QualityIssue.open(study.id(), null, QualityIssueType.INVALID_RANGE, QualitySeverity.ERROR,
                        null, null, "Age outside range"),
                QualityIssue.open(study.id(), null, QualityIssueType.FAST_SUBMISSION, QualitySeverity.WARNING,
                        null, null, "Submitted too quickly")));
        try (var executor = Executors.newVirtualThreadPerTaskExecutor(); var async = new Async(executor)) {
            var view = fx(() -> new QualityIssuesView(study, quality, null, null, async, failure -> fail(failure)));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (async.busy() && System.nanoTime() < deadline) Thread.sleep(10);
            assertFalse(async.busy());
            fx(() -> {
                var scene = new Scene(view.node(), 1180, 800);
                scene.getStylesheets().add(getClass().getResource("/css/theme.css").toExternalForm());
                view.node().applyCss(); view.node().layout();
                @SuppressWarnings("unchecked") var table = (TableView<QualityIssue>) view.node().lookup("#quality-issues-table");
                assertEquals(2, table.getItems().size());
                var search = (TextField) view.node().lookup("#quality-search-filter");
                search.setText("AGE"); assertEquals(1, table.getItems().size());
                @SuppressWarnings("unchecked") var severity = (ComboBox<QualitySeverity>) view.node().lookup("#quality-severity-filter");
                severity.setValue(QualitySeverity.WARNING); assertTrue(table.getItems().isEmpty());
                search.clear(); assertEquals(1, table.getItems().size());
                assertEquals(QualityIssueType.FAST_SUBMISSION, table.getItems().getFirst().type());
                view.node().lookupAll(".button").stream().map(node -> (Button) node)
                        .filter(button -> button.getText().equals("Clear filters")).findFirst().orElseThrow().fire();
                assertEquals(2, table.getItems().size());
                var open = QualityIssue.open(study.id(), null, QualityIssueType.INVALID_RANGE,
                        QualitySeverity.ERROR, UUID.randomUUID(), UUID.randomUUID(), "Age is outside the allowed range.");
                var resolved = new QualityIssue(UUID.randomUUID(), study.id(), null, open.type(), open.severity(),
                        QualityIssueStatus.RESOLVED, open.responseId(), open.questionId(), "Already fixed", "Checked source",
                        Instant.now(), Instant.now());
                table.getItems().setAll(open, resolved);
                table.getSelectionModel().select(0);
                assertNotNull(view.node().lookup("#quality-correct-answer"));
                var details = view.node().lookup("#quality-selection-details");
                assertTrue(details.lookupAll(".label").stream().map(node -> ((Label) node).getText())
                        .anyMatch(text -> text.contains("Age is outside")));
                table.getSelectionModel().clearAndSelect(1);
                assertNull(view.node().lookup("#quality-correct-answer"));
                assertTrue(details.lookupAll(".label").stream().map(node -> ((Label) node).getText())
                        .anyMatch(text -> text.contains("Read-only")));
                table.getSelectionModel().select(0);
                assertNull(view.node().lookup("#quality-correct-answer"));
                assertTrue(view.node().lookupAll(".button").stream().filter(node -> ((Button) node).getText().equals("Accept selected"))
                        .allMatch(node -> node.isDisabled()));
                table.getSelectionModel().clearSelection();
                assertTrue(details.lookupAll(".label").stream().map(node -> ((Label) node).getText())
                        .anyMatch(text -> text.contains("Select an issue")));
                return null;
            });
        }
    }

    @Test void correctionRejectsInvalidNumbersAndLongReasonsBeforeClosing() throws Exception {
        fx(() -> {
            var variable = new DatasetVariable(UUID.randomUUID(), UUID.randomUUID(), "Form", "age", "Age",
                    QuestionType.NUMBER, true, 18d, 100d, List.of());
            var dialog = CorrectionEditorDialog.create(variable, "150");
            var pane = dialog.getDialogPane();
            var reason = (TextArea) pane.lookup(".text-area"); reason.setText("Checked the source");
            var replacement = (TextField) pane.lookup(".text-field");
            assertEquals("150", replacement.getText());
            var ok = (Button) pane.lookupButton(ButtonType.OK);
            ok.fire();
            assertTrue(pane.lookupAll(".label").stream().map(node -> ((Label) node).getText())
                    .anyMatch(text -> text.contains("no greater than")));
            replacement.setText("NaN"); ok.fire();
            assertTrue(pane.lookupAll(".label").stream().map(node -> ((Label) node).getText())
                    .anyMatch(text -> text.contains("finite number")));
            replacement.setText("24"); reason.setText("x".repeat(1001)); ok.fire();
            assertTrue(pane.lookupAll(".label").stream().map(node -> ((Label) node).getText())
                    .anyMatch(text -> text.contains("1,000")));
            reason.setText("Verified source"); ok.fire();
            assertEquals("24", dialog.getResult().rawValue());
            return null;
        });
    }

    private static <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS);
    }
}
