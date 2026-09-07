package researchflow.service;

import researchflow.domain.FindingStatus;
import researchflow.domain.QualityIssueStatus;
import researchflow.domain.ReportDocument;
import researchflow.persistence.PersistenceException;
import researchflow.report.ReportHtmlRenderer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Composes a {@link ReportDocument} from currently stored Study/quality/version/finding data and
 * exports it as a self-contained HTML file. The report is never itself persisted — it is always a
 * fresh composition, so it can never drift from what those tables currently say. Only unapproved
 * findings are excluded; approved ones are never silently dropped.
 */
public final class ReportService {
    private final StudyService studies;
    private final FormService forms;
    private final ResponseQueryService responses;
    private final QualityService quality;
    private final VersionService versions;
    private final FindingService findings;
    private final AuditService audits;

    public ReportService(StudyService studies, FormService forms, ResponseQueryService responses,
                         QualityService quality, VersionService versions, FindingService findings, AuditService audits) {
        this.studies = studies;
        this.forms = forms;
        this.responses = responses;
        this.quality = quality;
        this.versions = versions;
        this.findings = findings;
        this.audits = audits;
    }

    public ReportDocument compose(UUID studyId) {
        var study = studies.find(studyId).orElseThrow(() -> new IllegalArgumentException("Study not found: " + studyId));
        var activeVersion = versions.active(studyId).orElse(null);
        var issues = quality.list(studyId, null);
        var qualitySummary = new ReportDocument.QualitySummary(
                issues.stream().filter(issue -> issue.status() == QualityIssueStatus.OPEN).count(),
                issues.stream().filter(issue -> issue.status() == QualityIssueStatus.ACCEPTED).count(),
                issues.stream().filter(issue -> issue.status() == QualityIssueStatus.DEFERRED).count(),
                issues.stream().filter(issue -> issue.status() == QualityIssueStatus.RESOLVED).count());
        var approved = findings.list(studyId).stream().filter(finding -> finding.status() == FindingStatus.APPROVED).toList();
        return new ReportDocument(study, activeVersion, forms.list(studyId).size(), responses.list(studyId).size(),
                qualitySummary, approved, defaultLimitations(activeVersion), Instant.now());
    }

    /** Composes, writes the HTML file, and records a {@code REPORT_GENERATED} audit event. */
    public Path export(UUID studyId, Path targetFile) {
        var document = compose(studyId);
        try {
            Files.writeString(targetFile, ReportHtmlRenderer.render(document), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new PersistenceException("Could not write the report file.", exception);
        }
        audits.record(studyId, "REPORT_GENERATED", "STUDY", studyId,
                "{\"approvedFindings\":" + document.approvedFindings().size() + "}");
        return targetFile;
    }

    private static List<String> defaultLimitations(researchflow.domain.DatasetVersion activeVersion) {
        var limitations = new LinkedHashSet<String>();
        limitations.add("Each finding reflects the dataset version in effect when its analysis was run; later "
                + "corrections or exclusions are not retroactively reflected in an already-approved finding.");
        if (activeVersion == null) limitations.add("No dataset version has been created for this Study yet.");
        limitations.add("Approved findings are researcher-reviewed statements; unapproved analyses are not included.");
        limitations.add("Correlational and group-comparison results describe association, not causation.");
        return List.copyOf(limitations);
    }
}
