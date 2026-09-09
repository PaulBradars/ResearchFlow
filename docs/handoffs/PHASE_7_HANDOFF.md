# Phase 7 handoff: Visualization, findings, and reporting

Date: 2026-09-05  
Phase status: implementation complete; clean build and test suite passing  
Architecture reference: `../architecture/CURRENT_IMPLEMENTED_ARCHITECTURE.md`

## Delivered outcome

- `ChartSpec` (sealed: `Bar`, `Histogram`, `Scatter`) and `ChartBuilder`, which choose and build a
  chart directly from an already-computed, structured `AnalysisResult` — never from raw AI prose.
  Frequency → bar; numeric summary → histogram; correlation → scatter; cross-tabulation and
  group comparison → a flattened/two-bar chart (grouped bars and box plots remain optional per the
  reduced-MVP chart set).
- `AnalysisService.chartValues` re-derives the raw numeric values a histogram/scatter needs from
  the *same immutable dataset-version snapshot* the analysis itself was computed against — the
  chart can never silently diverge from the version it illustrates.
- `Finding`/`FindingStatus` and `FindingService`: draft a finding directly from a computed
  `EvidenceBundle` (with its chart, if one could be derived), edit its wording, approve, or reject.
  A finding's `analysisId`/`datasetVersionId` are fixed at creation and untouched by wording edits.
- A "Create finding from this evidence" action on both the manual Run tab and Ask Your Data.
- `ReportService`/`ReportHtmlRenderer`: composes a report fresh from currently stored Study/quality/
  version/finding data (never persisted itself, so it can never drift) and exports it as a
  self-contained HTML file with charts as inline SVG — no external assets, no extra dependency.
  Only **approved** findings are included; a `REPORT_GENERATED` audit event is recorded on export.
- A new "Findings / Report" workspace (previously a disabled nav stub): a Findings tab (review
  queue: edit/approve/reject) and a Report tab (native JavaFX preview + "Export HTML…").
- One new migration (`V004`) adding two nullable `findings` columns (`evidence_summary`,
  `chart_json`) — no other schema change was needed; `findings.analysis_id`/`dataset_version_id`
  and the whole `analyses`/`dataset_versions` chain were already in place since V001.

## User walkthrough

1. Run a manual analysis (or ask a question via Ask Your Data) to get evidence.
2. Select **Create finding from this evidence**. A dialog opens with a plain-language draft
   (editable) and, when derivable, a live chart preview.
3. Confirm to save the finding as a **Draft**.
4. Open **Findings / Report** → **Findings**, select the finding, edit wording if needed, then
   **Approve** or **Reject**.
5. Open the **Report** tab, select **Preview** to see the composed report (study summary, quality
   summary, approved findings with their charts, and limitations), then **Export HTML…** to save a
   self-contained file anyone can open in a browser.

## Why the chart survives a restart without re-parsing arbitrary stored JSON

Reconstructing `AnalysisResult` from a historical `analyses.result_json` blob (five different
shapes, one with a nested count matrix) was judged out of scope for this increment — Phase 5
deliberately never built a reader for it, only a writer. Instead, a finding's chart is built
**once**, at creation time, from the fresh in-memory `EvidenceBundle` + raw chart values, then
serialized into `findings.chart_json` — a shape *this phase fully controls* (every `ChartSpec`
variant is a flat list of primitives, so its codec is simple and lives entirely in
`JdbcFindingRepository`, symmetric with how `JdbcAnalysisRepository` already owns the
`analyses.result_json` encoding). Re-reading a finding later — even after an application restart —
just reads that same simple JSON back. This satisfies "every chart traces to a stored analysis and
version" (via the finding's `analysisId`/`datasetVersionId` columns) and "a report remains
consistent after restart" without a general-purpose JSON parser.

## Test inventory added

| Test | Protects |
|---|---|
| `ChartBuilderTest` | Correct chart type/content per method, including the histogram/scatter raw-value requirement and their empty-data fallback |
| `SvgChartRendererTest` | One SVG mark per data point/category for each chart type; HTML-special-character escaping |
| `JdbcFindingRepositoryTest` | Create/edit/approve/reject, the chart JSON round trip, and that the analysis/version link survives a wording edit |
| `ReportServiceTest` | Report composition includes only approved findings; HTML export writes a self-contained file and records `REPORT_GENERATED` |

Verification baseline:

```text
Tests run: 91, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Migration

`V004__finding_evidence_columns.sql` adds `findings.evidence_summary` and `findings.chart_json`
(both nullable `ALTER TABLE ADD COLUMN`, safe on an existing database). `MigrationRunner` now
registers V001 through V004.

## Guardrails

- Charts are built only from already-computed, structured `AnalysisResult`/raw version-bound data —
  never from AI-generated text.
- A finding's `analysisId`/`datasetVersionId` never change after creation; only `edit` (wording) is
  mutable, and it touches no other column.
- Unapproved findings are excluded from the report by default; nothing silently promotes a Draft.
- The report is always a fresh composition, never a persisted, potentially stale document.
- Keep database and file-export work off the JavaFX thread (unchanged — routed through `Async`).
- Never edit an already-applied migration.

## Phase 8 starting point (release candidate)

1. Run all documented workflows end to end (collect → import → quality → version → analysis → Ask
   Your Data → chart/finding → report) and fix any integration gaps found.
2. Add cancellation/progress behavior for longer-running quality scans, analysis, and AI requests.
3. Test failure paths explicitly: database failure, malformed AI output, unavailable AI, empty
   datasets, missing variables, and snapshot restore.
4. Expand the seed to the planned 100–300-response demonstration dataset with known distributions.
5. Update the ER/component/sequence diagrams to match the final implementation and write
   installation/run instructions, a limitations list, and a demonstration script.
