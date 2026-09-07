# Phase 5 handoff: Deterministic analysis and evidence

Date: 2026-09-04  
Phase status: implementation complete; clean build and test suite passing  
Architecture reference: `../architecture/CURRENT_IMPLEMENTED_ARCHITECTURE.md`

## Delivered outcome

- `AnalysisPlan`, `AnalysisFilter`, `AnalysisResult` (sealed, one variant per method), and
  `EvidenceBundle` contracts — every analytical claim exposes method, variables, filters, sample
  size, results, warnings, and an explicit `dataset_version_id`.
- Schema-aware `AnalysisPlanValidator`: rejects unknown variables, incompatible method/type
  combinations, duplicate variables, and invalid filters before any calculation runs.
- Five `AnalysisStrategy` implementations behind a Simple Factory registry: frequency/percentage,
  numeric summary, correlation (Pearson), cross-tabulation, and a two-group comparison (Welch's
  t-statistic, its Welch–Satterthwaite degrees of freedom, and Cohen's d).
- `AnalysisService` — interpret (validate) -> resolve an explicit dataset version -> choose a
  strategy -> execute -> persist evidence — usable entirely without AI, ready for Phase 6 to front
  with natural-language plan generation.
- Analyses read the **immutable version snapshot** (`version_answer_snapshots`), never the live
  `answers` table, so a stored plan re-run against the same version reproduces its original result
  even if live data changes afterward.
- A manual "Analysis" workspace (previously a disabled nav stub): a plan builder + evidence panel,
  and a history tab with a raw plan/result/warnings details view.
- No new migration: the `analyses` table's columns already matched this design from V001.

## User walkthrough

1. Open a Study containing responses and select **Analysis**.
2. Choose a method, primary (and secondary, where required) variable, an optional single filter,
   and a dataset version — leave the version as **Active (default)** to use the current active
   version, auto-creating a baseline snapshot first if the Study has none yet.
3. Select **Run analysis**. The Evidence panel shows the method, variables, sample size, any
   warnings, and the typed result.
4. Select **History** to see every persisted analysis (method, version, sample size, source, time)
   and **View details** for its raw stored plan/result/warnings.

## Detection-free, deterministic methods

| Method | Variable requirements | Result |
|---|---|---|
| Frequency / percentage | any type | Category counts/percentages (multi-select contributes to every selected value) + missing count |
| Numeric summary | NUMBER | n, missing, mean, median, sample SD, min, max |
| Correlation | two NUMBER variables | Pearson r over pairwise-complete data |
| Cross-tabulation | two categorical variables (single choice/yes-no/Likert/rating) | Contingency table over pairwise-complete data |
| Two-group comparison | NUMBER outcome + a categorical variable yielding exactly two groups | Group n/mean/SD, mean difference, Welch t, Welch df, Cohen's d |

No p-value is reported for the group comparison: computing one correctly requires a t-distribution
CDF (an incomplete-beta special function), which would either need an external statistics
dependency or a hand-rolled implementation that is hard to independently verify. The t-statistic,
its degrees of freedom, and Cohen's d effect size are all textbook arithmetic, fully covered by
`StatisticsTest` against hand-computed values, and sufficient for the reduced-MVP "group-comparison
result" the roadmap calls for.

## Deterministic warnings

Every evidence bundle is checked for: sample size below 10, more than 20% missingness among
eligible responses for the analysis, and (for correlation/group comparison) a fixed non-causal
interpretation note. Warnings never suppress a result; they are additional context alongside it.

## Version-binding contract

`AnalysisService.run` resolves the plan's `datasetVersionId` — an explicit version, or (when
`null`) the Study's active version, auto-creating a baseline snapshot via `VersionService` if none
exists yet. `AnalysisRepository.loadVersionData` then reconstructs every typed `Answer` from that
version's `version_answer_snapshots` rows (excluded responses are dropped), so the computation
input is frozen at snapshot time. `JdbcAnalysisRepositoryTest.reRunningTheSamePlanAgainstAnExplicit
VersionReproducesTheResultEvenAfterLiveDataChanges` proves this: correcting a live answer after a
version snapshot exists does not change a later re-run of a plan bound to that version.

## Persistence

`JdbcAnalysisRepository` writes `analyses(plan_json, result_json, warnings_json, sample_size,
dataset_version_id, source='MANUAL')` in one transaction alongside an `ANALYSIS_RUN` audit event.
JSON is hand-built (matching the codebase's existing no-JSON-library convention), and is written
but never re-parsed back into typed objects — the history view intentionally shows the raw JSON
rather than reconstructing a full `EvidenceBundle`, keeping the persistence contract simple while
still satisfying traceability. `SnapshotJson` (extracted from `JdbcVersionRepository` in this
phase) is shared by both dataset-version restore and analysis reads, so the one small stored JSON
shape is decoded in exactly one place.

## Test inventory added

| Test | Protects |
|---|---|
| `StatisticsTest` | Mean/median/sample SD/Pearson r/Welch comparison against hand-computed textbook values |
| `AnalysisStrategiesTest` | Each strategy's output shape and edge cases (missing values, excluded responses, >2 groups) |
| `AnalysisPlanValidatorTest` | Type/variable/filter rejection for every method before execution |
| `JdbcAnalysisRepositoryTest` | Full run() round trip per method, auto-created baseline version, persisted history/details, and the version-binding reproducibility guarantee |

Verification baseline:

```text
Tests run: 46, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Phase 6 starting point

1. Define `LlmClient` and a local-runtime adapter with configuration, health check, timeout,
   cancellation, and clear error mapping.
2. Provide the model with variable IDs, labels, types, supported operations, and a strict
   `AnalysisPlan` JSON schema; parse its output defensively and reject unknown methods/variables/filters.
3. Build an `AnalysisFacade` around the existing `AnalysisService`: interpret -> validate ->
   `AnalysisService.run` -> store evidence -> chart request -> explain. AI never computes a result
   itself.
4. Constrain the explanation prompt to the stored `EvidenceBundle` only.
5. Store model/runtime identifier and prompt-template version alongside AI-produced text
   (`analyses.source='AI'`, `model_metadata_json`, already provisioned).
6. Preserve manual analysis and evidence access when the LLM is offline — `AnalysisRunView` and
   `AnalysisService` already work with no AI dependency.

## Guardrails

- The LLM (Phase 6+) interprets and explains; this deterministic Java code validates and
  calculates. Never let AI compute a statistic or bypass `AnalysisPlanValidator`.
- Never execute AI-generated SQL; `AnalysisPlan` variables are always resolved against the
  authoritative `FormRepository`/`Question` metadata, never a free-text column reference.
- Every analysis binds to an explicit `dataset_version_id`; never compute against "current live
  data" implicitly.
- Keep database work off the JavaFX thread; keep SQL and formulas out of JavaFX (`AnalysisRunView`
  only renders an already-computed `EvidenceBundle`).
- Never edit an already-applied migration.
