package researchflow.app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import researchflow.ui.Navigator;
import researchflow.util.Logging;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ResearchFlowApplication extends Application {
    private static final Logger LOG = Logger.getLogger(ResearchFlowApplication.class.getName());
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        final AppConfig config;
        try { config = AppConfig.load(); }
        catch (IllegalArgumentException invalid) {
            showStartupFailure(stage, invalid.getMessage()); stage.show(); return;
        }
        Logging.configure(config.logDirectory());
        Thread.setDefaultUncaughtExceptionHandler((thread, failure) ->
                LOG.log(Level.SEVERE, "UNCAUGHT_UI_FAILURE [" + failure.getClass().getSimpleName() + "]"));

        stage.setTitle("ResearchFlow AI");
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.setScene(loadingScene());
        stage.show();

        executor.submit(() -> {
            try {
                var services = AppServices.initialize(config);
                Platform.runLater(() -> showApplication(stage, services));
            } catch (Throwable failure) {
                LOG.log(Level.SEVERE, "APPLICATION_STARTUP_FAILED [" + failure.getClass().getSimpleName() + "]");
                Platform.runLater(() -> showStartupFailure(stage, "Check that the data directory is writable, then review the local log file."));
            }
        });
    }

    private void showApplication(Stage stage, AppServices services) {
        var navigator = new Navigator(services, executor);
        var scene = new Scene(navigator.root(), 1180, 760);
        scene.getStylesheets().add(ResearchFlowApplication.class.getResource("/css/theme.css").toExternalForm());
        stage.setScene(scene);
        LOG.info("APPLICATION_READY");
    }

    private static Scene loadingScene() {
        var content = new VBox(14, new ProgressIndicator(), new Label("Opening ResearchFlow workspace…"));
        content.setAlignment(Pos.CENTER);
        return new Scene(content, 960, 640);
    }

    private static void showStartupFailure(Stage stage, String detail) {
        var title = new Label("ResearchFlow AI could not start");
        title.getStyleClass().add("page-title");
        var message = new Label(detail);
        message.setWrapText(true);
        var content = new VBox(12, title, message);
        content.setAlignment(Pos.CENTER);
        var scene = new Scene(content, 960, 640);
        var css = ResearchFlowApplication.class.getResource("/css/theme.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        stage.setScene(scene);
    }

    @Override
    public void stop() {
        executor.shutdownNow();
    }
}
