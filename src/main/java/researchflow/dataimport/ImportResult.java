package researchflow.dataimport;

import java.util.List;
import java.util.UUID;

public record ImportResult(UUID formId, int importedCount, int skippedCount, List<ImportRowError> errors) {
    public ImportResult {
        errors = List.copyOf(errors);
    }
}
