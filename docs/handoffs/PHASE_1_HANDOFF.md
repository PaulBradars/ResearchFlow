# Phase 1 handoff: Foundation and Study workspace

Date: 2026-09-02  
Phase status: implementation complete; clean build and test suite passing

For a class-by-class description of the code that exists now and an inventory of current versus
planned design patterns, see `../architecture/CURRENT_IMPLEMENTED_ARCHITECTURE.md`.

## Delivered outcome

Phase 1 establishes a runnable JavaFX application and the parent research workspace:

- Maven build targeting Java 21 with JavaFX 21 and SQLite JDBC.
- Asynchronous startup, migration, seed, repository, and dashboard work off the JavaFX thread.
- Persistent navigation shell with Studies Home and an identifiable Study Dashboard context.
- Create, read, update, list, and archive behavior for studies.
- Study validation for required title, length limits, date range, and research questions.
- Archive is intentionally non-destructive; archived studies remain queryable and auditable.
- Versioned migration runner and complete initial lifecycle schema.
- Connection factory enabling SQLite foreign keys and busy timeout on every connection.
- Transaction helper with rollback and a repository that saves study changes, research questions,
  and their audit event atomically.
- Idempotent development study seed.
- Structured local JSON-line logs containing event/error metadata, not research answer values.
- Startup and operation error boundaries with user-safe messages.
- Repository/integration tests for migrations, foreign keys, rollback, restart persistence,
  Study CRUD/archive/audit behavior, and validation.

## Architecture and important decisions

The application is one Maven module with package boundaries matching the roadmap. Only packages
needed by Phase 1 contain behavior; the initial schema reserves stable relationships for later phases.

```text
researchflow/
  app/          configuration, composition root, startup
  ui/           JavaFX views, navigation, async/error boundaries
  domain/       Study, StudyStatus, StudyMetrics
  service/      validation and Study use cases
  persistence/  migrations, connections, transactions, JDBC repository, seed
  util/         privacy-safe structured logging
```

Programmatic JavaFX was selected for the small foundation UI. The rationale and the threshold for
switching large screens to FXML are recorded in `../adr/0001-programmatic-javafx-views.md`.

The central persistence rule is: service validation happens before repository work; repository
changes and their audit event commit together. The application opens a connection per operation
instead of sharing a mutable global connection.

## Database baseline

`V001__initial_schema.sql` creates:

- `studies`, `research_questions`
- `forms`, `form_sections`, `questions`, `question_options`
- `responses`, `answers`
- `dataset_versions`, `version_answer_snapshots`
- `quality_issues`, `analyses`, `findings`
- `audit_logs`, `chat_references`

Foreign keys, status checks, uniqueness constraints, and critical lookup indexes are included.
Migration application is tracked in `schema_migrations`. Never edit an applied migration; add
`V002__description.sql` and register it in `MigrationRunner`.

## How to start from this handoff

1. Install JDK 21 and Maven 3.9+ and ensure both commands are on `PATH`.
2. Run `mvn clean test` from the repository root.
3. Run `mvn javafx:run`.
4. Confirm the seeded “Student Wellbeing and Academic Focus” study appears.
5. Create a second study, restart the app, and confirm it persists.
6. Open it, return to Studies Home, edit it, and archive it.

Local state is under `data/` and logs under `logs/`. Both are ignored by Git.

## Test inventory

| Test | Protects |
|---|---|
| `MigrationRunnerTest` | Complete/idempotent schema and per-connection foreign keys |
| `TransactionManagerTest` | No partial writes after a failure |
| `JdbcStudyRepositoryTest` | CRUD, ordered research questions, audit events, archive, restart persistence |
| `StudyValidatorTest` | Required fields/date rules and valid-study acceptance |

Verification completed with a portable Temurin JDK 21.0.12.1 and Maven 3.9.16 on 2026-09-02:

```text
Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The portable verification tools and dependency cache are isolated under the Git-ignored `.tools/`
directory. Normal development only requires the prerequisites in `README.md`.

## Phase 2 starting point

Begin with domain/state behavior before the builder UI:

1. Add `Form`, `Section`, `Question`, `QuestionOption`, `Response`, and typed `Answer` domain models.
2. Implement Draft/Active/Closed state behavior and unit tests. Structural edits must be Draft-only;
   submissions must be Active-only.
3. Add form/question repositories against the existing tables. Do not expose JDBC to controllers.
4. Implement a `ResponseSubmissionService` that validates all answers, then inserts the response,
   answers, and `RESPONSE_SUBMITTED` audit event through one `TransactionManager` transaction.
5. Add an integration test with a forced answer-insert failure and assert that no response remains.
6. Build the Form workspace and `QuestionControlFactory` for the reduced MVP types.
7. Enable the currently disabled Form and Responses navigation items only when their workflows have
   loading, empty, success, and failure states.

Recommended first Phase 2 vertical slice: create a Draft form with one required short-text question,
activate it, submit one response transactionally, and verify it after restart. Add the remaining
question types only after this slice is stable.

## Guardrails to keep

- Controllers/views contain no SQL and no statistical formulas.
- Never log answer values or send them to an error message.
- Keep database work off the JavaFX thread.
- Do not hard-delete a study; archive it.
- Preserve UUID question IDs when labels change, because they become stable dataset variable IDs.
- Every future multi-table change and its audit record must use one transaction.
- Standard tests must not require an LLM.

## Known Phase 1 boundaries

- The Form through Findings navigation entries are intentionally disabled placeholders.
- There is no hard-delete/restore UI; archive is the safe deletion policy for the MVP.
- User identity is a local placeholder (`local-researcher`) until identity requirements are defined.
- Migration discovery is an explicit ordered list, which is easy to audit for a semester project;
  each new migration must be added to that list.
- No JavaFX UI automation is included yet. Business and persistence behavior is tested below the UI.
