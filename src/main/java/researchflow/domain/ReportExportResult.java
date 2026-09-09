package researchflow.domain;

import java.nio.file.Path;
import java.time.Instant;

public record ReportExportResult(Path path, String sha256, Instant exportedAt, boolean auditRecorded, String warning) { }
