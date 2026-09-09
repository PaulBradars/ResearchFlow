package researchflow.app;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
class AppConfigTest {
    @TempDir Path directory;
    AppConfig config(String url, String model, int timeout) { return new AppConfig(directory.resolve("db.sqlite"), directory.resolve("logs"), false, false, url, model, timeout); }
    @Test void validatesEndpointTimeoutAndModelWithoutLeakingCredentials() {
        for(var url : new String[]{"oops", "file:///tmp/db", "http://user:secret@localhost", "http://localhost?token=secret"}) {
            var error = assertThrows(IllegalArgumentException.class, () -> config(url,"",60));
            assertFalse(error.getMessage().contains("secret"));
        }
        assertThrows(IllegalArgumentException.class, () -> config("http://localhost", "", 0));
        assertThrows(IllegalArgumentException.class, () -> config("http://localhost", "bad model", 60));
        assertTrue(config("http://localhost:11434", "", 60).aiDiagnostics().contains("automatic selection"));
    }
    @Test void rejectsDirectoryAsDatabaseAndMalformedTimeoutSetting() {
        assertThrows(IllegalArgumentException.class, () -> new AppConfig(directory, directory.resolve("logs"), false,false,"http://localhost","",60));
        var old = System.getProperty("researchflow.ai.timeoutSeconds");
        try { System.setProperty("researchflow.ai.timeoutSeconds", "abc"); assertTrue(assertThrows(IllegalArgumentException.class, AppConfig::load).getMessage().contains("whole number")); }
        finally { if(old == null) System.clearProperty("researchflow.ai.timeoutSeconds"); else System.setProperty("researchflow.ai.timeoutSeconds", old); }
    }
}
