package researchflow.dataimport;

import java.util.List;
import java.util.UUID;

public record ImportResult(UUID formId, int totalRows, int importedCount, int skippedCount, int failedCount,
                           boolean setupSucceeded, State state, String message, List<ImportRowError> errors) {
    public enum State { COMPLETE, FAILED, CANCELLED }
    public ImportResult {
        errors = List.copyOf(errors);
    }
    public boolean completedNormally() { return state == State.COMPLETE; }
    public int unprocessedCount() { return totalRows - importedCount - skippedCount - failedCount; }
    public String summary() {
        return state + ": " + importedCount + " imported, " + skippedCount + " invalid/skipped, " + failedCount
                + " failed, " + unprocessedCount() + " unprocessed out of " + totalRows
                + ". Setup " + (setupSucceeded ? "succeeded. " : "did not finish. ") + message;
    }
}
