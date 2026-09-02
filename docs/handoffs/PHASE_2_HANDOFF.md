# Phase 2 handoff: Form design and transactional collection

Date: 2026-09-02  
Phase status: implementation complete; clean build and all tests passing

## Delivered outcome

Phase 2 completes the first end-to-end collection workflow:

- Immutable Form, Section, Question, QuestionOption, Response, and typed Answer models.
- Explicit Draft/Active/Closed State behavior and one-way transitions.
- Draft form creation and question add/edit/delete/reorder behavior.
- Stable Question UUIDs across label/settings edits.
- Required, choice-option, and numeric range settings.
- Preview and active respondent-entry views for all reduced-MVP question types.
- Service-boundary validation with field-level respondent messages.
- Atomic response, answer, and submission-audit persistence.
- Form creation/update/activation/closure and response-submission audit events.
- Separate researcher and respondent UI modes.
- Enabled Form and Responses navigation with loading and empty states.

See `../architecture/CURRENT_IMPLEMENTED_ARCHITECTURE.md` for class responsibilities and diagrams.

## Operational walkthrough

1. Run `mvn javafx:run` and open an active Study.
2. Select **Form**, then **New form**.
3. Add at least one question. For choice/Likert/rating types, enter one option per line.
4. Use **Preview** while the form is Draft.
5. Select **Activate**. Structural controls become unavailable.
6. Select **Collect response**, complete the form, and submit.
7. The application opens **Responses**, where the persisted summary appears.
8. Return to **Form** and select **Close form** when collection is complete.

Lifecycle is intentionally irreversible in this phase. Create a new instrument rather than rewriting
a Closed one.

## Validation contract

Design validation includes form title length, question label, variable-key syntax/uniqueness,
numeric range ordering, and choice options. Activation additionally requires at least one question.

| Submission type | Rules |
|---|---|
| All | Required fields; unknown Question IDs rejected |
| Number | Numeric, finite, optional minimum/maximum |
| Single choice / Likert / rating | Exactly one configured option |
| Multiple choice | Configured options only; no duplicates |
| Yes/no | Only Yes or No |
| Date | Valid ISO date from the date control |

No response row is written when any answer is invalid.

## Transaction and privacy guarantees

`JdbcResponseRepository.submit` uses one `TransactionManager` callback for the response row, every
answer row, and `RESPONSE_SUBMITTED`. A test installs a database trigger that aborts an answer insert
and verifies the response rolls back.

Audit events contain entity metadata only. Answer values are not written to logs, audit JSON, error
messages, or the response-summary workspace.

## Tests added

| Test | Protects |
|---|---|
| `FormStateTest` | State permissions and transitions |
| `JdbcFormRepositoryTest` | Structure, stable Question ID, lifecycle guards, audit events |
| `ResponseSubmissionServiceTest` | Draft/Closed submission rejection before persistence |
| `ResponseSubmissionIntegrationTest` | Typed validation, atomic success, forced-failure rollback |

Verification result:

```text
Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Normal development uses `mvn clean test`. This workspace's portable verification toolchain can be
used with:

```powershell
$env:JAVA_HOME='D:\research_flow\.tools\jdk\jdk-21.0.12.1+1'
& 'D:\research_flow\.tools\maven\apache-maven-3.9.16\bin\mvn.cmd' --% -Dmaven.repo.local=D:\research_flow\.tools\m2 clean test
```

## Phase 3 starting point

1. Map stable Question UUIDs to dataset columns and decode typed answer storage.
2. Build a bounded dataset table with missing display, sorting, search, and typed filters.
3. Add response details without moving answer-query SQL into JavaFX.
4. Route corrections through a service/Command boundary; never write directly from table cells.
5. Save correction reason and audit metadata atomically.
6. Add audit timeline queries and any new indexes through a registered migration.

## Guardrails to retain

- Preserve Question UUIDs; they are dataset variable IDs.
- Keep structural edits Draft-only and submissions Active-only.
- Keep answer values out of logs and audit details.
- Validate all fields before response persistence.
- Keep response/answers/audit in one transaction.
- Keep JDBC and formulas out of JavaFX.
- Keep database work off the JavaFX application thread.
- Never edit an already-applied migration.
- Keep standard tests independent of an LLM and network.
