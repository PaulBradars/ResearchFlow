# Phase 7 findings and report flow

```mermaid
flowchart TB
    UI1[AnalysisRunView / AskYourDataView] -->|Create finding| EA[EvidenceActions]
    EA -->|chartValues: same version snapshot| AS[AnalysisService]
    EA --> CB[ChartBuilder]
    CB --> CS[ChartSpec]
    EA --> FED[FindingEditorDialog]
    FED -->|draft text + chart| FS[FindingService.draft]
    FS --> FR[FindingRepository]
    FR --> DB1[(findings: analysis_id, dataset_version_id, chart_json)]

    UI2[FindingsWorkspaceView] -->|edit / approve / reject| FS
    FS --> DB1

    UI3[ReportWorkspaceView] -->|compose| RS[ReportService]
    RS --> ST[StudyService]
    RS --> QS[QualityService]
    RS --> VS[VersionService]
    RS --> FS
    RS --> RD[ReportDocument]
    RD --> UI3
    UI3 -->|Export HTML| RS
    RS --> HR[ReportHtmlRenderer]
    HR --> SVG[SvgChartRenderer]
    RS -->|write file + REPORT_GENERATED| AUD[AuditService]
```

A finding's chart is built once, at creation time, from the fresh `EvidenceBundle` and raw
version-bound values, then persisted as `findings.chart_json` — a shape this phase fully controls
(every `ChartSpec` variant is a flat list of primitives). This lets a report re-render the same
chart after a restart without needing to parse the richer, five-shaped `analyses.result_json`.
`ReportService.compose` is never persisted itself; every preview or export is a fresh read of
current Study/quality/version/finding data, so it can never drift from what those tables say.
