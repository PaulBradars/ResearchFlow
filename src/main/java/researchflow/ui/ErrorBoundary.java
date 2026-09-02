package researchflow.ui;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ErrorBoundary {
    private static final Logger LOG = Logger.getLogger(ErrorBoundary.class.getName());
    private final BorderPane root = new BorderPane();
    private final Label banner = new Label();

    public ErrorBoundary() {
        banner.getStyleClass().add("error-banner");
        banner.setVisible(false);
        banner.setManaged(false);
        BorderPane.setMargin(banner, new Insets(0, 20, 8, 20));
        root.setTop(banner);
    }

    public BorderPane node() {
        return root;
    }

    public void show(Node content) {
        banner.setVisible(false);
        banner.setManaged(false);
        root.setCenter(content);
    }

    public void showError(String userMessage, Throwable failure) {
        LOG.log(Level.SEVERE, userMessage + " [" + failure.getClass().getSimpleName() + "]");
        banner.setText(userMessage);
        banner.setVisible(true);
        banner.setManaged(true);
    }

    public void showFatal(String userMessage, Throwable failure) {
        showError(userMessage, failure);
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("ResearchFlow AI");
        alert.setHeaderText("The application could not continue");
        alert.setContentText(userMessage);
        alert.showAndWait();
    }
}
