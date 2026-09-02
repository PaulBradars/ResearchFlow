package researchflow.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class Logging {
    private Logging() {
    }

    public static void configure(Path directory) {
        try {
            Files.createDirectories(directory);
            var root = Logger.getLogger("");
            for (var handler : root.getHandlers()) {
                root.removeHandler(handler);
            }
            var file = new FileHandler(directory.resolve("researchflow-%g.log").toString(), 1_000_000, 5, true);
            file.setFormatter(new JsonLogFormatter());
            file.setLevel(Level.INFO);
            root.addHandler(file);
            root.setLevel(Level.INFO);
        } catch (IOException exception) {
            System.err.println("ResearchFlow logging could not be initialized: " + exception.getClass().getSimpleName());
        }
    }
}
