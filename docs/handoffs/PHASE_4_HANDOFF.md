# Phase 4 handoff: Quality review and dataset versioning

Date: 2026-09-04  
Phase status: implementation complete; clean build and test suite passing  
Architecture reference: `../architecture/CURRENT_IMPLEMENTED_ARCHITECTURE.md`

## Delivered outcome

- Chain-of-Responsibility `QualityHandler`s: `MissingRequiredHandler`, `InvalidRangeHandler`,
  `DuplicateResponseHandler`, `OutlierHandler`, `FastSubmissionHandler`.
- Deterministic `QualityIssue` persistence (type, severity, status, affected response/question,
  explanation, resolution note), reconciled against active issues on every scan so a still-open
  condition is never duplicated.
- A `ReviewCommand` sealed Command hierarchy (`Correct`, `Exclude`, `Accept`, `Defer`) interpreted
  solely by `QualityReviewService`; every branch requires a reason or note and produces an audited
  status change.
- Response exclusion (the schema's `EXCLUDED` status, never a delete) is now reachable through
  quality review.
- Dataset version snapshots capturing every current answer and response status, with
  parent/version-number/reason/change-summary/active tracking.
- Safe restore: rewrites live data to match an earlier version's snapshot, then records a brand
  new active version. Earlier versions, including the one restored from, are never rewritten.
- A "Quality / Versions" workspace with an **Issues** tab (scan, status filter, single and bulk
  review actions with confirmation and affected counts) and a **Versions** tab (create snapshot,
  restore).
- `V003__quality_review_indexes.sql` adds supporting indexes. `quality_issues`, `dataset_versions`,
  and `version_answer_snapshots` already existed from V001, so no structural migration was needed.

## User walkthrough

1. Open a Study containing responses.
2. Select **Quality / Versions** → **Issues**.
3. Select **Scan for quality issues**. Deterministic rules run against the live dataset; nothing
   changes automatically.
4. Double-click an issue (or select it and choose **Review selected**) to see its explanation and
   available actions.
5. Choose **Correct** (only offered when the issue references one response and variable) to apply
   a validated typed replacement, exactly like the Dataset workspace's controlled correction; the
   issue resolves alongside it.
6. Choose **Exclude response**, **Accept**, or **Defer** — each requires a reason/note and a
   confirmation.
7. Select multiple rows and use **Accept selected** / **Defer selected** / **Exclude selected** for
   a bulk action; the confirmation states the affected count.
8. Select **Versions** and choose **Create version snapshot** after a cleaning batch, providing a
   reason and optional change summary.
9. Select an earlier, non-active version and choose **Restore selected version**. This rewrites
   live data to match that version and creates a new active version rather than editing history.

## Detection rules (deterministic, no AI)

| Rule | Trigger |
|---|---|
| Missing required | A required question has no recorded (or blank) answer for a response. |
| Invalid range | A NUMBER answer falls outside its question's configured minimum/maximum. |
| Duplicate response | A response's full answer set exactly matches an earlier response on the same form; only the later one is flagged. |
| Outlier | A NUMBER answer falls outside Tukey's 1.5×IQR fences for its question (minimum 4 samples). |
| Fast submission | Duration is below `max(5s, 2s × question count)` for the form. |

The Phase 3 development seed already embeds one of the last three cases (a duplicate pair, a
14-hour sleep outlier, and a 3-second submission) — verified by `SeederQualityScanTest` — so the
demo path needs no additional fixture.

## Reconciliation contract

A scan never edits existing issues. It fingerprints each detected condition as
`(type, response, question)` and inserts only fingerprints with no existing OPEN/ACCEPTED/DEFERRED
issue. Accepting or deferring an issue keeps it from being re-reported as new, while resolving one
(correct/exclude) allows the same fingerprint to reappear only if the underlying condition recurs.

## Transaction and audit contract

- `Exclude`, `Accept`, and `Defer` update `quality_issues` and insert an audit event in one
  transaction.
- `Correct` reuses the already-audited `DatasetCorrectionService` transaction, then marks the issue
  RESOLVED as a second step. If the second step fails, the correction itself remains valid,
  audited, and unaffected — the issue simply stays open for a retry.
- Version creation and restore snapshot every current answer/response-status and update
  `is_active` in one transaction, followed by a `DATASET_VERSION_CREATED` /
  `DATASET_VERSION_RESTORED` audit event.
- Restore never deletes or edits `dataset_versions` / `version_answer_snapshots` rows belonging to
  another version; it only writes live `answers` / `responses` and appends a new version.

## Migration

`V003__quality_review_indexes.sql` adds `quality_issues(response_id)`, `quality_issues(question_id)`,
and `version_answer_snapshots(response_id)` indexes. No table changes were needed.

## Test inventory added

| Test | Protects |
|---|---|
| `QualityHandlerChainTest` | Each handler's detection rule in isolation, plus chain composition |
| `JdbcQualityRepositoryTest` | Scan reconciliation/dedup, Correct/Exclude/Accept review actions, audit, rejection of an unknown issue |
| `JdbcVersionRepositoryTest` | Snapshot content, parent/version numbering, restore rewriting live data as a new version without touching history |
| `SeederQualityScanTest` | The Phase 3 seed's embedded duplicate/outlier/fast-submission examples are detected, with no false positives |

Verification baseline:

```text
Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Phase 5 starting point

1. Define `AnalysisPlan`, `AnalysisContext`, `AnalysisResult`, and `EvidenceBundle` contracts, each
   binding to an explicit `dataset_version_id` (`VersionService.active(studyId)` is the default).
2. Implement schema-aware plan validation against the authoritative Form/Question metadata already
   exposed by `FormRepository`.
3. Implement `AnalysisStrategy` plus a registry for frequency/percentage, numeric summary,
   correlation, cross-tabulation, and one two-group comparison.
4. Persist plan/result/sample size/warnings/version reference through the already-provisioned
   `analyses` table.
5. Build a manual analysis panel and evidence panel before any AI integration.

## Guardrails

- Quality detection never changes data; only a `ReviewCommand` (researcher-confirmed, reasoned)
  does.
- Keep issue explanations free of literal answer values; reference variable labels and computed
  ranges only.
- Dataset versions are append-only; restore creates a new version rather than rewriting history.
- Bind future analyses to an explicit `dataset_version_id`, not "current live data".
- Bulk actions must show the affected count and require confirmation.
- Keep database work off the JavaFX thread; keep SQL and formulas out of JavaFX.
- Never edit an already-applied migration.
