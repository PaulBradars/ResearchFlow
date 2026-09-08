package researchflow.report;

import researchflow.domain.ReportDocument;
import researchflow.visualization.SvgChartRenderer;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ReportHtmlRenderer {
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private static final String CSS = "body{font-family:'Segoe UI',Arial,sans-serif;max-width:820px;margin:40px auto;"
            + "color:#172033;padding:0 20px;} h1{margin-bottom:4px;} "
            + "h2{margin-top:32px;border-bottom:1px solid #e2e7f0;padding-bottom:6px;} "
            + ".muted{color:#667085;} .finding{border:1px solid #e2e7f0;border-radius:8px;padding:14px;margin:12px 0;} "
            + "svg{max-width:100%;height:auto;}";

    private ReportHtmlRenderer() { }

    public static String render(ReportDocument document) {
        var builder = new StringBuilder();
        builder.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>")
                .append(escape(document.study().title())).append(" — Research report</title>")
                .append("<style>").append(CSS).append("</style></head><body>");

        builder.append("<h1>").append(escape(document.study().title())).append("</h1>");
        builder.append("<p class=\"muted\">Generated ").append(TIMESTAMP.format(document.generatedAt())).append("</p>");
        if (!document.study().description().isBlank()) {
            builder.append("<p>").append(escape(document.study().description())).append("</p>");
        }
        if (!document.study().objectives().isBlank()) {
            builder.append("<h2>Objectives</h2><p>").append(escape(document.study().objectives())).append("</p>");
        }

        builder.append("<h2>Study summary</h2><ul>")
                .append("<li>Forms: ").append(document.formCount()).append("</li>")
                .append("<li>Responses: ").append(document.responseCount()).append("</li>")
                .append("<li>Active dataset version: ").append(versionSummary(document)).append("</li>")
                .append("</ul>");

        var quality = document.qualitySummary();
        builder.append("<h2>Quality summary</h2><ul>")
                .append("<li>Open: ").append(quality.openIssues()).append("</li>")
                .append("<li>Accepted: ").append(quality.acceptedIssues()).append("</li>")
                .append("<li>Deferred: ").append(quality.deferredIssues()).append("</li>")
                .append("<li>Resolved: ").append(quality.resolvedIssues()).append("</li>")
                .append("</ul>");

        builder.append("<h2>Approved findings</h2>");
        if (document.approvedFindings().isEmpty()) {
            builder.append("<p class=\"muted\">No findings have been approved yet.</p>");
        } else {
            for (var finding : document.approvedFindings()) {
                builder.append("<div class=\"finding\"><p>").append(escape(finding.text())).append("</p>");
                if (finding.evidenceSummary() != null && !finding.evidenceSummary().isBlank()) {
                    builder.append("<p class=\"muted\">").append(escape(finding.evidenceSummary())).append("</p>");
                }
                if (finding.chart() != null) builder.append(SvgChartRenderer.render(finding.chart()));
                builder.append("</div>");
            }
        }

        builder.append("<h2>Limitations</h2><ul>");
        for (var limitation : document.limitations()) builder.append("<li>").append(escape(limitation)).append("</li>");
        builder.append("</ul>");

        builder.append("</body></html>");
        return builder.toString();
    }

    private static String versionSummary(ReportDocument document) {
        var version = document.activeVersion();
        return version == null ? "none yet" : "v" + version.versionNumber() + " — " + escape(version.reason());
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}

