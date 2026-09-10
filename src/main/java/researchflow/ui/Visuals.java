package researchflow.ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/** Small vector assets that stay crisp without image files or external fonts. */
final class Visuals {
    private Visuals() { }

    static javafx.scene.Node icon(String name) {
        var shape = new SVGPath();
        shape.setContent(switch (name) {
            case "Form" -> "M4 2 H16 V20 H4 Z M7 7 H13 M7 11 H13 M7 15 H11";
            case "Responses" -> "M3 3 H17 V14 H10 L5 19 V14 H3 Z M6 7 H14 M6 10 H12";
            case "Dataset", "Import" -> "M2 4 H18 V18 H2 Z M2 9 H18 M7 4 V18 M12 4 V18";
            case "Quality / Versions" -> "M10 2 L18 5 V10 Q18 16 10 20 Q2 16 2 10 V5 Z M6 10 L9 13 L14 7";
            case "Analysis" -> "M3 18 V11 M10 18 V3 M17 18 V7 M1 21 H20";
            case "Findings / Report" -> "M4 2 H13 L18 7 V20 H4 Z M13 2 V7 H18 M7 11 H14 M7 15 H14";
            default -> "M2 2 H8 V8 H2 Z M12 2 H18 V8 H12 Z M2 12 H8 V18 H2 Z M12 12 H18 V18 H12 Z";
        });
        shape.setFill(Color.TRANSPARENT); shape.setStroke(Color.web("#7d91ad")); shape.setStrokeWidth(1.6);
        var box = new StackPane(shape); box.setMinSize(22, 24); box.setPrefSize(22, 24); box.setMaxSize(22, 24);
        box.setMouseTransparent(true);
        return box;
    }

    static HBox hero(String eyebrow, String title, String subtitle) {
        var overline = new Label(eyebrow); overline.getStyleClass().add("hero-eyebrow");
        var heading = new Label(title); heading.getStyleClass().add("hero-title"); heading.setWrapText(true);
        var description = new Label(subtitle); description.getStyleClass().add("hero-subtitle"); description.setWrapText(true);
        var words = new VBox(9, overline, heading, description); words.setMinWidth(0);
        words.setAlignment(javafx.geometry.Pos.CENTER_LEFT); HBox.setHgrow(words, Priority.ALWAYS);
        var graphic = constellation();
        var hero = new HBox(24, words, graphic); hero.getStyleClass().add("hero-panel");
        hero.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        graphic.visibleProperty().bind(hero.widthProperty().greaterThan(740));
        graphic.managedProperty().bind(graphic.visibleProperty());
        return hero;
    }

    private static Canvas constellation() {
        var canvas = new Canvas(230, 112);
        canvas.setMouseTransparent(true);
        var g = canvas.getGraphicsContext2D();
        double[][] points = {{14,80},{59,32},{108,68},{159,15},{206,50},{170,99}};
        g.setStroke(Color.web("#8097c0", .32)); g.setLineWidth(1);
        for (int i = 0; i < points.length; i++) for (int j = i + 1; j < points.length; j++) {
            if (j - i < 3) g.strokeLine(points[i][0], points[i][1], points[j][0], points[j][1]);
        }
        for (int i = 0; i < points.length; i++) {
            var color = Color.web(i % 2 == 0 ? "#61dac4" : "#9bb8ff");
            g.setFill(color.deriveColor(0, 1, 1, .10)); g.fillOval(points[i][0]-14, points[i][1]-14, 28, 28);
            g.setFill(color); g.fillOval(points[i][0]-4, points[i][1]-4, 8, 8);
        }
        return canvas;
    }

    static VBox emptyState(String icon, String title, String subtitle) {
        var mark = icon(icon);
        var heading = new Label(title); heading.getStyleClass().add("section-title");
        var hint = new Label(subtitle); hint.getStyleClass().add("muted"); hint.setWrapText(true);
        var box = new VBox(15, mark, heading, hint); box.getStyleClass().add("empty-state");
        box.setAlignment(javafx.geometry.Pos.CENTER); return box;
    }

    static VBox panel(String title, javafx.scene.Node... content) {
        var label = new Label(title); label.getStyleClass().add("section-title");
        var box = new VBox(12, label); box.getChildren().addAll(content); box.getStyleClass().add("content-panel");
        return box;
    }
}
