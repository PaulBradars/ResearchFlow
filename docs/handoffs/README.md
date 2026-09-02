# ResearchFlow AI

ResearchFlow AI is a local-first JavaFX research workspace. Phases 1–3 provide Study management,
form design and lifecycle, transactional response collection, a searchable/filterable bounded dataset,
controlled audited corrections, and a Study audit timeline.

## Prerequisites

- JDK 21
- Apache Maven 3.9+

Verify the toolchain:

```powershell
java -version
mvn -version
```

## Build, test, and run

```powershell
mvn clean test
mvn javafx:run
```

The first run creates `data/researchflow.db`, applies every migration, and adds one development
study with an active demonstration form and eight responses. Open that Study and use **Dataset** to
inspect the sample. Use **Form** to design another instrument or **Collect response** for respondent
entry. Logs are JSON lines under `logs/`; research answer values are never logged.

Configuration can be supplied as a JVM property or environment variable:

| Purpose | JVM property | Environment variable | Default |
|---|---|---|---|
| Database path | `researchflow.db` | `RESEARCHFLOW_DB` | `data/researchflow.db` |
| Log directory | `researchflow.logs` | `RESEARCHFLOW_LOGS` | `logs` |
| Development seed | `researchflow.seed` | `RESEARCHFLOW_SEED` | `true` |

Example:

```powershell
mvn javafx:run -Dresearchflow.seed=false -Dresearchflow.db=data/my-study.db
```

To reset development data, close the application and remove the explicit files
`data/researchflow.db`, `data/researchflow.db-shm`, and `data/researchflow.db-wal`. Do not delete
the whole project directory.

## Current user workflow

1. Open Studies Home.
2. Create a study with metadata and research questions.
3. Double-click it to enter the persistent study context.
4. Open **Form**, create a Draft form, and add or reorder questions.
5. Preview and activate the form, then use **Collect response** for respondent entry.
6. Inspect saved submissions in **Responses** and close collection when complete.
7. Open **Dataset** to search, filter, sort, inspect details, or make a confirmed correction.
8. Inspect traceability under the **Audit timeline** tab.
9. Return, edit the study, or archive it. Archive retains all data and audit history.

See [Phase 3 handoff](docs/handoffs/PHASE_3_HANDOFF.md) for the verified workflow, query/correction contracts,
test coverage, and Phase 4 starting point. Earlier phase handoffs remain historical records.

See [current implemented architecture](docs/architecture/CURRENT_IMPLEMENTED_ARCHITECTURE.md) for the implemented
dependency flow, class responsibilities, runtime sequences, and current design-pattern inventory.

The complete documentation index is available at [docs/README.md](docs/README.md).
