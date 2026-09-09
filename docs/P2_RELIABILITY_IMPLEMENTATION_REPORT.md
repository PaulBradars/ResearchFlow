# ResearchFlow AI: P2 reliability implementation report

Date: 9 September 2026

The P2 reliability changes are implemented and the complete clean Maven Wrapper build passes **132 tests: 0 failures, 0 errors, 0 skipped**. This includes **13 new tests** and the existing P1 integrity regressions. Existing uncommitted work was preserved. The starting workspace already contained partial P2 implementations; this report describes the consolidated implementation, completion work, and verification, not a claim that every listed component was authored from scratch in this turn. Application data under `data/` was not opened, migrated, or modified.

## 1. Confirmed issues and root causes

| Area | Finding and root cause | Final behavior |
|---|---|---|
| Recovery | Database-level recovery needs a consistent SQLite copy and coordination beyond individual repository transactions. Partial recovery code validated migration names but did not check all required current columns. | Online backup, staged validation/migration, schema validation, safety backup, exclusive final restore, and a recovery screen. |
| History | Stored JSON lacked durable original variable labels and was not a sufficient typed UI contract. | Schema-aware typed reconstruction for all five existing methods, saved labels for new evidence, explicit legacy/unsupported notices, historical charts and finding drafting. |
| Import | Setup and response rows commit separately; operational failure previously lacked a complete lifecycle receipt. | Counts, setup/state reporting, cancellation between rows, progress, exportable errors, and preservation of committed rows. |
| Export | Filesystem publication and database audit cannot share one SQLite transaction. | Verified staged HTML, atomic publication, SHA-256 receipt, separate audit outcome. |
| Tasks | Executor submission alone does not prevent duplicate work or callbacks into abandoned views. | Disposable workspace scopes, per-action pending handles, interruption, guarded UI dispatch, and transaction cancellation checks. |
| Acceptance | P1 tests did not exercise recovery, typed reopening, partial import, export-audit failure, or task disposal. | Headless integration workflow plus focused failure regressions. |
| Release | No Wrapper, CI, or runtime image configuration; Maven lifecycle plugin defaults varied by Maven version. | Official pinned Wrapper, pinned lifecycle plugins, Windows/Linux CI, runtime-image Maven profile and Windows script. |
| Configuration | `Integer.parseInt` and permissive boolean parsing produced low-level errors or silently wrong values; startup config failures escaped the failure view. | Actionable validation and startup display, directory checks, credential-free diagnostics. |
| Provenance | Live counts and current version could be mistaken for the population behind an old finding. | Explicit live/current summary and per-finding historical analysis/version/filter/warning/source metadata. |

The initial suite run had one failure: the existing migration inventory expected five migrations while the partial P2 implementation registered six. The inventory was updated to six; the P1 behavior assertions were not weakened.

## 2. Implementation and before/after semantics

Recovery uses the SQLite online backup/restore APIs, not raw main-file copying. The selected backup is read-only, copied to staging, migrated there, and checked for SQLite integrity, valid references, supported migration history, and required schema columns. Current application operations use a connection-factory gate; recovery waits exclusively and saves a safety backup before SQLite restores the destination transaction. The recovery screen prevents navigation during this operation. Invalid files and incompatible schemas are rejected before active database replacement. See [recovery instructions](RECOVERY_AND_RELEASE.md).

V006 adds `analyses.evidence_schema_version` (default 0) and `analyses.variables_json`. New results use schema 1 and save original variable labels. Stored plans/results decode to typed domain records; filters, warnings, sample sizes, immutable version IDs, timestamps, source, and stored model metadata survive restart. Schema 0 uses explicit ID labels, not invented historical wording. Invalid JSON, missing required fields, unsupported schema versions, and sample-size mismatches return unsupported evidence. Existing numerical methods are unchanged. Degenerate old numeric records with nonfinite JSON cannot be reconstructed and remain visibly unsupported.

Import returns a COMPLETE, FAILED, or CANCELLED receipt with total/imported/skipped/failed/unprocessed counts and setup status. A failed row stops operational processing; invalid input rows are skipped. Setup and committed rows remain. The UI provides cooperative cancellation, progress every 100 processed rows, and an outcome export. This is explicitly not an atomic import.

Report export stages and reads back HTML, hashes it, then atomically publishes it. Only afterwards does it attempt the report audit with timestamp, hash, and included finding IDs. `ReportExportResult` distinguishes audit failure from file failure; the UI uses this detailed result. The compatibility `export` method returns the file path. Report composition remains readable for archived studies, while export remains guarded.

Async scopes cancel on workspace navigation and suppress disposed/cancelled callbacks. Duplicate submissions at the same action site share the pending handle until completion. Callbacks remain on the JavaFX dispatcher. Interruption is checked before database commit, so an interrupted current transaction rolls back; prior commits are not undone. Recovery's operation gate prevents previously running workspace operations from writing after final restoration. Cancellation remains cooperative and does not guarantee immediate termination of CPU work or native calls.

## 3. Files and components

Paths are relative to the repository root. This inventory includes the partial P2 files present at the start and the completion changes.

| Files | Purpose |
|---|---|
| `service/DatabaseRecoveryService.java`, `util/AtomicFiles.java`, `ui/RecoveryView.java` | Online backup, validated recovery, staging and publication |
| `persistence/ConnectionFactory.java`, `persistence/TransactionManager.java` | Recovery operation gate and cancellation before commit |
| `analysis/EvidenceDecoder.java`, `util/Json.java`, `domain/HistoricalEvidence.java`, `domain/StoredAnalysis.java` | Strict typed historical evidence reconstruction |
| `persistence/JdbcAnalysisRepository.java`, `persistence/MigrationRunner.java`, `db/migration/V006__historical_evidence_metadata.sql` | Persist evidence schema and labels; register new migration |
| `service/AnalysisService.java`, `ui/AnalysisHistoryView.java`, `ui/AnalysisWorkspaceView.java` | Reopen evidence, charts, findings, and provenance |
| `service/DatasetImportService.java`, `dataimport/ImportResult.java`, `service/CancellationToken.java`, `ui/ImportWorkspaceView.java` | Explicit partial import outcomes and cancellation |
| `service/ReportService.java`, `domain/ReportExportResult.java`, `domain/ReportDocument.java`, `report/ReportHtmlRenderer.java`, `ui/ReportWorkspaceView.java` | Atomic HTML publication, audit receipt, historical provenance |
| `ui/Async.java`, `ui/Navigator.java` | Workspace lifecycle, action deduplication, callback suppression, recovery navigation lock |
| `app/AppConfig.java`, `app/ResearchFlowApplication.java`, `app/AppServices.java` | Configuration validation, startup messages, service wiring |
| `src/test/java/researchflow/persistence/P2ReliabilityTest.java` | Nine database/workflow failure and restart tests |
| `src/test/java/researchflow/app/AppConfigTest.java`, `src/test/java/researchflow/ui/AsyncTest.java` | Four configuration and task-lifecycle tests |
| `src/test/java/researchflow/persistence/MigrationRunnerTest.java` | Six-migration inventory |
| `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties`, `pom.xml` | Official Wrapper and reproducible plugin/runtime packaging configuration |
| `.github/workflows/build.yml`, `scripts/package-desktop.ps1` | CI and Windows app-image generation |
| `README.md`, `docs/README.md`, `docs/RECOVERY_AND_RELEASE.md`, this report, current architecture reference | Setup, recovery, release and verification documentation |

Java component paths above are under `src/main/java/researchflow/`; migration paths are under `src/main/resources/`.

## 4. Database compatibility

V001–V005 were not edited by this task. V006 changes only evidence metadata columns. Old evidence remains stored unchanged and is decoded only when read. Recovery upgrades a staged V005 backup to V006 without changing its source. Future migration histories and a claimed-current database missing a required column are regression-tested rejections. P1 complete response membership, answer presence, exact-restore restrictions, reapproval, atomic correction, archive guards, target validation, and stale warnings remain intact.

## 5. Tests and verification

`P2ReliabilityTest` adds:

1. `onlineBackupRestoreAndInvalidRecoveryPreserveData`
2. `futureMigrationAndActiveFileAreRejected`
3. `olderBackupIsMigratedOnStageWithoutChangingSource`
4. `claimedCurrentDatabaseWithMissingSchemaIsRejected`
5. `cancellationBeforeTransactionCommitRollsBack`
6. `importReportsOperationalFailureAfterCommittedRows`
7. `importCancellationReturnsPartialReceipt`
8. `reportDistinguishesAuditFailureAndPublicationFailure`
9. `desktopWorkflowAndAllEvidenceTypesSurviveRestart`

The workflow creates a study/form, activates it, submits responses, inspects/corrects data, scans/reviews quality, saves a snapshot, runs all five analyses with filters and source metadata, reopens persistence, reconstructs evidence and chart inputs, drafts and approves findings, exports, reopens again, and checks legacy/invalid evidence and old-version provenance. Import has separate integration coverage. Restart means newly initialized service/repository objects over the same database, not a relaunched JavaFX process.

`AppConfigTest` adds endpoint/model/timeout/credential-message and invalid-path/timeout-setting checks. `AsyncTest` verifies deduplication, disposed callback suppression, and dispatcher-only delivery. These are headless tests with a controlled dispatcher, not a JavaFX automation suite.

Targeted clean verification: **33 passed**, including 13 new tests and 20 existing P1 integrity/migration tests.

Full clean command:

```powershell
$env:MAVEN_USER_HOME = "D:\research_flow\.tools\wrapper-home"
.\mvnw.cmd -B -ntp -o "-Dmaven.repo.local=D:\research_flow\.tools\m2" clean verify
```

Result: **132 tests, 132 passed, 0 failures, 0 errors, 0 skipped**. The final `clean verify` also built the application jar and completed in 26.428 seconds. Maven 3.9.11 compiled 179 main files and 37 test files on Oracle JDK 26.0.2.1, targeting Java 25. The test run used the workspace dependency cache and temporary SQLite databases. No real model or application database was contacted. Existing SQLite native-access and Maven/JDK compatibility warnings did not fail the build.

The `desktop` Maven packaging profile also built successfully with runtime dependencies. `jpackage --type app-image` completed successfully and generated `.tools/desktop/ResearchFlow/ResearchFlow.exe`, its application jars, and bundled runtime. Remote CI and interactive packaged desktop operation were not exercised. No manual JavaFX walkthrough, GUI automation, clean-machine installation, power-loss simulation, full-disk simulation, or large-data benchmark was performed.

## 6. Remaining risks and release checklist

Implemented:

- [x] Consistent application backup and staged validated recovery with a safety backup.
- [x] Typed reopening of all five analysis types, with explicit legacy/unsupported states.
- [x] Partial-import reporting, cancellation, progress, and exportable errors.
- [x] Staged HTML export with separate file/audit outcomes and hash.
- [x] Workspace task handles, deduplication, stale callback suppression, transaction cancellation.
- [x] Headless workflow/restart acceptance coverage and preserved P1 tests.
- [x] Maven Wrapper, pinned lifecycle plugins, CI configuration, and documented JDK/launch commands.
- [x] Configuration validation and credential-free endpoint/model diagnostics.
- [x] Live, active-snapshot, and historical-finding provenance distinctions.

Partially implemented improvements:

- [ ] CSV streaming: preview still loads the entire file. Errors are exportable, not automatically journaled; leaving a view can discard its unexported receipt.
- [ ] Cancellation latency: CPU/native operations may continue until their next boundary; completed writes/files are not rolled back by later cancellation.
- [ ] Historical freshness: only active-snapshot equality is compared; older-version live equality is explicitly unevaluated.
- [ ] Release packaging: a Windows runtime-image profile/script is provided; signed installers and installer configuration remain future work.
- [ ] GUI acceptance: the service/persistence equivalent passes; manual desktop checks remain listed in the recovery/release guide.

Future release work:

- Run remote CI and manual acceptance on JDK 25 and each target OS; verify the packaged application on a fresh machine.
- Exercise restoration under external database locks, concurrent external processes, low disk space, and abrupt process/power failure. The in-process gate is not a cross-process application lock.
- Add backup retention/rotation/encryption if required; current snapshots, absent records, and safety backups accumulate.
- Benchmark large databases, import preview, report provenance reads, and cancellation latency.

The minimum P2 implementation is complete, but these unchecked release qualifications should be completed before calling the application production-ready.

## References

The recovery transaction design follows the [SQLite online backup API](https://www.sqlite.org/c3ref/backup_finish.html). Wrapper scripts come from the official [Apache Maven Wrapper distribution](https://maven.apache.org/tools/wrapper/maven-wrapper-distribution/index.html); installation and pinning follow the [Maven Wrapper documentation](https://maven.apache.org/tools/wrapper/index.html).
