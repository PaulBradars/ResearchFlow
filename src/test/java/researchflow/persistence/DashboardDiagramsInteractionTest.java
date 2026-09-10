package researchflow.persistence;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import researchflow.domain.*;
import researchflow.service.*;
import researchflow.ui.Async;
import researchflow.ui.DashboardDiagramsView;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class DashboardDiagramsInteractionTest {
    @TempDir Path directory;

    @BeforeAll static void toolkit() throws Exception {
        var ready = new CountDownLatch(1);
        try { Platform.startup(ready::countDown); }
        catch (IllegalStateException alreadyStarted) { ready.countDown(); }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        fx(() -> { Platform.setImplicitExit(false); return null; });
    }

    @Test void emptyDashboardRefreshesAndChartsOnlyTheSavedSnapshot() throws Exception {
        var connections = TestDatabase.migrated(directory);
        var transactions = new TransactionManager(connections);
        var studies = new JdbcStudyRepository(connections, transactions);
        var study = new StudyService(studies).create("Diagrams", "", "", "", null, null, List.of());
        var guard = new StudyWriteGuard(studies);
        var forms = new JdbcFormRepository(connections, transactions);
        var formService = new FormService(forms, guard);
        var form = formService.create(study.id(), "Survey", "");
        var age = Question.create("age", "Age", "", QuestionType.NUMBER, true, 0d, 100d, List.of());
        form = formService.updateStructure(form.id(), form.title(), form.description(),
                List.of(form.sections().getFirst().withQuestions(List.of(age))));
        form = formService.activate(form.id());
        var submissions = new ResponseSubmissionService(forms, new JdbcResponseRepository(connections, transactions), guard);
        submissions.submit(form.id(), Instant.now().minusSeconds(60), Map.of(age.id(), "20"));
        submissions.submit(form.id(), Instant.now().minusSeconds(60), Map.of(age.id(), "40"));
        var analysis = new AnalysisService(forms, new JdbcVersionRepository(connections, transactions),
                new JdbcAnalysisRepository(connections, transactions), guard);
        var opened = new AtomicBoolean();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor(); var async = new Async(executor)) {
            var view = fx(() -> new DashboardDiagramsView(study, analysis, async, () -> opened.set(true)));
            await(async);
            fx(() -> {
                new Scene(view.node(), 760, 800);
                assertTrue(((Label) view.node().lookup("#dashboard-diagram-status")).getText().contains("No statistical diagrams"));
                return null;
            });
            var evidence = analysis.run(study.id(), new AnalysisPlan(AnalysisMethod.NUMERIC_SUMMARY, age.id(), null, List.of(), null));
            // A later live submission must not appear in a chart for the older saved analysis.
            submissions.submit(form.id(), Instant.now().minusSeconds(60), Map.of(age.id(), "90"));
            fx(() -> { button(view, "Refresh diagrams").fire(); return null; });
            await(async);
            fx(() -> {
                view.node().applyCss(); view.node().layout();
                @SuppressWarnings("unchecked") var selector = (ComboBox<AnalysisSummary>) view.node().lookup("#dashboard-diagram-selection");
                assertEquals(evidence.id(), selector.getValue().id());
                var chart = (BarChart<?, ?>) view.node().lookup("#dashboard-statistical-chart");
                assertNotNull(chart);
                assertEquals(2, chart.getData().getFirst().getData().stream()
                        .mapToInt(point -> ((Number) point.getYValue()).intValue()).sum());
                assertFalse(button(view, "Refresh diagrams").isDisabled());
                button(view, "Open analysis workspace").fire();
                assertTrue(opened.get());
                return null;
            });
        }
    }

    private static Button button(DashboardDiagramsView view, String text) {
        return view.node().lookupAll(".button").stream().map(node -> (Button) node)
                .filter(button -> text.equals(button.getText())).findFirst().orElseThrow();
    }
    private static void await(Async async) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (async.busy() && System.nanoTime() < deadline) Thread.sleep(10);
        fx(() -> null);
        assertFalse(async.busy());
    }
    private static <T> T fx(Callable<T> work) throws Exception {
        var task = new FutureTask<>(work); Platform.runLater(task); return task.get(15, TimeUnit.SECONDS);
    }
}
