# Phase 3 handoff: Dataset workspace and audit baseline

Date: 2026-09-02  
Phase status: implementation complete; clean build and test suite passing  
Architecture reference: `../architecture/CURRENT_IMPLEMENTED_ARCHITECTURE.md`

## Delivered outcome

- Stable Question UUIDs mapped to human-readable dataset columns.
- Bounded 50-row UI pages with count metadata and a repository maximum of 100.
- Case-insensitive search across response ID, form title, and answer values.
- Contains, equals, missing, numeric/date greater-than, and numeric/date less-than filters.
- Submitted-time, duration, and variable sorting.
- Missing-value display and response-detail dialog.
- Read-only table cells with explicit correction entry points.
- Typed correction validation using the same parser as response submission.
- Required correction reason and confirmation.
- Atomic answer update/insert, correction history, and `ANSWER_CORRECTED` audit event.
- Basic Study audit timeline.
- V002 migration with correction history and lookup indexes.
- Idempotent development seed extended with an active four-question form and eight responses,
  including missing optional values and useful Phase 4 quality-review examples.

## User walkthrough

1. Open a Study containing submitted responses.
2. Select **Dataset**.
3. Search or select a variable/operator/value and choose **Apply**.
4. Select submitted time, duration, or variable sorting; choose a sort variable when required.
5. Use **Previous** / **Next** for bounded pages.
6. Double-click a row or select **Response details**.
7. Select **Correct** beside a field, provide a typed replacement and reason, then confirm.
8. Open **Audit timeline** and verify the `ANSWER_CORRECTED` event.

The timeline intentionally does not show answer values. Historical values remain in the local
`answer_corrections` table for future versioning and reproducibility work.

## Migration

`V002__dataset_audit_baseline.sql` creates `answer_corrections` and focused indexes. V001 was not
modified. Existing installations apply V002 automatically at startup; new installations apply V001
and V002 in order.

## Transaction contract

A correction is accepted only after the target response/question is resolved, the authoritative
Form is reloaded, the reason is validated, and the new value passes the original question rules.
The answer, correction-history record, and audit event share one transaction. A forced audit insert
failure is tested and leaves both the answer and correction history unchanged.

## Test inventory added

| Test | Protects |
|---|---|
| `JdbcDatasetRepositoryTest.boundedDataset...` | Page bounds/counts, variables, search, numeric filter, variable sort, details, correction, audit privacy |
| `JdbcDatasetRepositoryTest.correctionAndHistory...` | Rollback when correction audit insertion fails |
| Updated `MigrationRunnerTest` | V002 registration and correction table creation |

Verification baseline:

```text
Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Phase 4 starting point

1. Implement deterministic QualityHandlers for required missing, invalid range, duplicates, simple
   outliers, and unusually fast responses.
2. Persist reviewable QualityIssues without changing data automatically.
3. Introduce explicit Command objects for correction/exclusion actions and review results.
4. Create immutable dataset snapshots after meaningful cleaning batches.
5. Add parent/version reason/change summary and active-version selection.
6. Restore safely by creating a new version rather than rewriting history.
7. Bind later analyses to explicit dataset version IDs.

## Guardrails

- Keep dataset cells read-only.
- Require validation, reason, confirmation, history, and audit for corrections.
- Never put answer values in logs or audit JSON.
- Keep Question UUIDs stable.
- Bind SQL values; never execute user- or AI-generated SQL.
- Keep database work off the JavaFX thread.
- Never edit an applied migration.
- Do not auto-correct or delete data in quality review.
