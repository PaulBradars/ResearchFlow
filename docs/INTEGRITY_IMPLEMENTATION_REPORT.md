# ResearchFlow AI: P1 integrity implementation report

Date: 9 September 2026

All six reported P1 defects were confirmed from the source and addressed, together with active-version freshness detection. The final clean Maven build passed **119 tests**, including **20 new regression tests**. Existing working-directory changes were preserved. Application data under `data/` was not migrated or modified during this task; migration verification used temporary test databases.

## 1. Bugs confirmed

| Issue | Confirmation | Result |
|---|---|---|
| 1. Restore retained later data | Source inspection: `applySnapshot` only upserted captured answers and restored statuses of represented responses | Fixed for complete snapshots; regression-tested for later responses, later answers, exclusions, empty datasets, and failed restores |
| 2. Blank responses disappeared | Source inspection: snapshots started from `answers`, so zero-answer responses had no rows | Fixed through independent response membership; missing-count/denominator regression added |
| 3. Edited findings stayed approved | Source inspection: `updateText` changed only wording and update time | Changed approved wording now becomes Draft, with approval time cleared and previous wording retained |
| 4. Quality correction was not atomic | Source inspection: answer correction committed before a separate issue-resolution call | One repository transaction now commits or rolls back answer/history/issue/audits together |
| 5. Archived children remained writable | Source inspection: study metadata had a guard, but child services did not | Shared mandatory service guard and additional transactional repository checks |
| 6. Review targets were trusted | Source inspection: command-supplied study/response/question IDs were not compared with the issue | Mismatches rejected before writes; authoritative issue IDs used for execution |
| Active snapshot freshness | Source inspection: an existing active snapshot was reused after live changes without a freshness signal | Service exposes explicit freshness state; default-active analyses include a stale-data warning |

The original defects were confirmed by source inspection, not by running the new regression suite against an unmodified checkout. Regression tests verify the corrected behavior against temporary SQLite databases.

## 2. Root causes and architectural choices

### Restore and blank responses

Answer rows represented both values and implicit response membership. That representation cannot distinguish an absent response from a present response with no answers. Upsert-only restoration also cannot represent an answer that used to be absent.

The fix adds response-level version rows and separates physical record retention from live-dataset presence. This avoids deleting records referenced by correction history and allows a later snapshot to be restored again. Existing domain, repository, service, SQLite, and analysis boundaries remain in place.

### Finding approval

Approval was stored on a mutable finding row without being invalidated when text changed. The fix keeps the current finding model and records the previous text/status/approval time in a small revision table, then changes approved findings to Draft within the same transaction as the edit and audit. Saving identical normalized wording does not revoke approval or create a revision.

### Quality correction and target ownership

The service orchestrated two individually transactional repository calls. A successful first transaction could not be rolled back if the second failed. A new `QualityRepository.correctAndResolve` operation owns the whole transaction and invokes a connection-aware correction helper. It never starts a nested correction transaction.

The service first loads the issue and validates supplied IDs. For corrections, the issue must have a matching non-null response and question; for exclusions, a matching non-null response is required. Execution uses the loaded issue's IDs. The JDBC operation checks ownership again within its transaction, and correction verifies the target is still in the live dataset.

### Archive enforcement

UI objects and child entities did not carry an authoritative writable-study policy. `StudyWriteGuard` now reloads study state through `StudyRepository` and is a required dependency for independent mutating services. Study metadata uses this same policy. Entry services such as import, AI orchestration, and report export reuse guarded services before doing their work.

JDBC mutations also check archive state within their write transaction. This protects the database write when the study changes after the initial service check. The consistent exception is `IllegalStateException("Archived studies are read-only.")`.

## 3. Files changed

Paths below are relative to the repository root. This list describes changes made for this task, including edits to files that were already uncommitted/untracked beforehand.

### Application and domain

| File | Change |
|---|---|
| `src/main/java/researchflow/app/AppServices.java` | Construct and inject a shared write guard |
| `src/main/java/researchflow/domain/DatasetFreshness.java` — new | Define `NO_ACTIVE_VERSION`, `CURRENT`, `STALE`, `UNKNOWN_LEGACY` |
| `src/main/java/researchflow/domain/DatasetVersion.java` | Expose membership completeness on version projections |
| `src/main/java/researchflow/domain/VersionAnswer.java` | Document the empty-response sentinel: null question and answer |

### Services

| File | Change |
|---|---|
| `src/main/java/researchflow/service/StudyWriteGuard.java` — new | Central authoritative archive policy |
| `src/main/java/researchflow/service/StudyService.java` | Reuse the shared archive policy |
| `src/main/java/researchflow/service/FormService.java` | Guard create/edit/activate/close and expose a guarded study check for callers |
| `src/main/java/researchflow/service/ResponseSubmissionService.java` | Reject archived-study submissions |
| `src/main/java/researchflow/service/DatasetImportService.java` | Check archive state before import setup |
| `src/main/java/researchflow/service/DatasetCorrectionService.java` | Guard corrections and separate validated preparation from persistence |
| `src/main/java/researchflow/service/QualityService.java` | Guard scans because issue reconciliation writes data |
| `src/main/java/researchflow/service/QualityReviewService.java` | Guard review, validate ownership, invoke atomic correction |
| `src/main/java/researchflow/service/VersionService.java` | Guard create/restore; expose freshness |
| `src/main/java/researchflow/service/AnalysisService.java` | Guard persisted analyses; add stale/legacy warnings |
| `src/main/java/researchflow/service/AnalysisFacade.java` | Check archive state before AI work and persisted chat turns |
| `src/main/java/researchflow/service/FindingService.java` | Guard draft/edit/approve/reject |
| `src/main/java/researchflow/service/AuditService.java` | Guard standalone audit writes |
| `src/main/java/researchflow/service/ReportService.java` | Reject archived-study export before writing the file; composition stays readable |

### Persistence

| File | Change |
|---|---|
| `src/main/resources/db/migration/V005__dataset_membership_and_finding_revisions.sql` — new | Add presence flags, response snapshots, completeness metadata, and wording revisions |
| `src/main/java/researchflow/persistence/MigrationRunner.java` | Register V005 after unchanged V001–V004 |
| `src/main/java/researchflow/persistence/VersionRepository.java` | Add freshness contract |
| `src/main/java/researchflow/persistence/JdbcVersionRepository.java` | Capture complete membership; restore exact live presence; compare freshness; block unknowable legacy restores |
| `src/main/java/researchflow/persistence/JdbcAnalysisRepository.java` | Reconstruct from response snapshots with left-joined answers; guard writes |
| `src/main/java/researchflow/persistence/JdbcDatasetRepository.java` | Apply live-presence filters to grid/detail/search/filter/sort; provide shared transactional correction helper; guard audit writes |
| `src/main/java/researchflow/persistence/JdbcResponseRepository.java` | Filter live membership and answer presence; guard submission transactions |
| `src/main/java/researchflow/persistence/JdbcStudyRepository.java` | Correct live response counts; add transactional archive checks; reject stale archived-study overwrites |
| `src/main/java/researchflow/persistence/JdbcFormRepository.java` | Guard form writes within the transaction |
| `src/main/java/researchflow/persistence/QualityRepository.java` | Define atomic correction/resolution operation |
| `src/main/java/researchflow/persistence/JdbcQualityRepository.java` | Implement atomic correction; validate targets and archive state inside writes |
| `src/main/java/researchflow/persistence/JdbcFindingRepository.java` | Store previous wording, revoke approval on changed text, clear approval time on rejection, guard writes |
| `src/main/java/researchflow/persistence/JdbcChatRepository.java` | Use a guarded transaction for chat persistence |
| `src/main/java/researchflow/persistence/SnapshotJson.java` | Preserve exponent-form numbers and escaped literal backslashes during restore/analysis reads |

### UI

| File | Change |
|---|---|
| `src/main/java/researchflow/ui/DatasetVersionsView.java` | Show legacy membership limitations; disallow legacy exact restore; allow restoring a complete active snapshot; disable version writes for archived studies |
| `src/main/java/researchflow/ui/FindingsWorkspaceView.java` | Explain reapproval after wording changes and require saving edited wording before approval |

### Tests

| File | Change |
|---|---|
| `src/test/java/researchflow/persistence/DataIntegrityRegressionTest.java` — new | 18 workflow/integrity regressions |
| `src/test/java/researchflow/persistence/IntegrityMigrationTest.java` — new | 2 V004 upgrade/rollback regressions |
| `src/test/java/researchflow/persistence/MigrationRunnerTest.java` | Expect five migrations and the two new tables |
| `src/test/java/researchflow/persistence/AnalysisFacadeTest.java` | Inject guard into existing fixture services |
| `src/test/java/researchflow/persistence/DatasetImportServiceTest.java` | Inject guard into existing fixture services |
| `src/test/java/researchflow/persistence/JdbcAnalysisRepositoryTest.java` | Inject guard into existing fixture services |
| `src/test/java/researchflow/persistence/JdbcDatasetRepositoryTest.java` | Inject guard into existing fixture services |
| `src/test/java/researchflow/persistence/JdbcFindingRepositoryTest.java` | Inject guard into existing fixture services |
| `src/test/java/researchflow/persistence/JdbcFormRepositoryTest.java` | Inject guard into existing fixture services |
| `src/test/java/researchflow/persistence/JdbcQualityRepositoryTest.java` | Inject guard into existing fixture services |
| `src/test/java/researchflow/persistence/JdbcVersionRepositoryTest.java` | Inject guard into existing fixture services |
| `src/test/java/researchflow/persistence/ReportServiceTest.java` | Inject guard into existing fixture services |
| `src/test/java/researchflow/persistence/ResponseSubmissionIntegrationTest.java` | Inject guard into existing fixture services |
| `src/test/java/researchflow/persistence/SeederQualityScanTest.java` | Inject guard into existing fixture services |
| `src/test/java/researchflow/service/ResponseSubmissionServiceTest.java` | Supply an active-study repository fixture for the mandatory guard |

### Documentation

| File | Change |
|---|---|
| `docs/INTEGRITY_IMPLEMENTATION_REPORT.md` — new | This report, migration semantics, tests, and acceptance record |
| `README.md` | Explain version compatibility and archive/reapproval behavior; link report |
| `docs/README.md` | Index this implementation report |
| `docs/architecture/CURRENT_IMPLEMENTED_ARCHITECTURE.md` | Update integrity behavior, schema baseline, and verified test count |
| `PROJECT_ANALYSIS.md` | Mark the earlier review as a historical baseline and link completed fixes |

## 4. Database changes and compatibility

V005 adds:

- `responses.in_dataset`: live membership, default `1` for existing/current responses.
- `answers.is_present`: live answer presence, default `1`; physically retained absent answers remain available to correction history.
- `dataset_versions.membership_complete`: default `0` for old versions; new snapshots explicitly mark it `1` in their transaction.
- `version_response_snapshots`: one version/response row with exclusion state, including responses with no answers.
- `finding_text_revisions`: previous text, status, approval timestamp, and edit time.
- Indexes for response-snapshot references and finding revisions.

No previously applied migration was edited. V005 backfills only membership provable from historical answer snapshots. **It does not infer historical blank responses from current rows or timestamps.** Old versions and analyses remain readable, but old versions are labeled incomplete and cannot be restored through an operation promising exact membership. Re-running analysis on an old version carries an explicit population warning. Already stored analysis results remain unchanged.

On upgrade, create a new version from the live dataset to establish a complete baseline. This cannot reconstruct unknown membership in older versions. New snapshots and their restored successors support exact restoration. The migration is automatically applied on application startup and was verified against a populated V004 fixture, including idempotence, foreign-key validity, rollback on failure, and retry.

## 5. Fixes implemented: observable behavior

### Exact restore semantics

For a complete target version, restore reproduces its response membership, answer presence, typed answer values, and exclusion state. It first marks this study's live membership/answer presence absent, then restores target members and captured values, and finally creates a new active version. All these steps and the restore audit execute in one transaction.

Later responses and later-added answers cease to appear in live dataset readers, response summaries, quality scans, counts, and new snapshots. Their physical rows are retained. Restoring a later saved version can bring them back. Other studies and previous snapshots are unchanged. An empty version restores an empty live dataset. Restoring the active version is permitted because live data may have changed since it was created.

During related edge-case review, the snapshot codec was found to truncate scientific-notation numbers and mishandle literal backslash sequences through ordered string replacements. Those narrow decoding defects were fixed and covered by an exact-value restore regression.

### Blank responses and denominators

Analysis reconstruction starts from version response rows and left-joins answers. A zero-answer response contributes an empty answer map to the eligible population. Missing counts, missing filters, and warnings therefore include it. Excluded blank responses remain represented but do not contribute to the analysis population. Statistical algorithms themselves were not expanded or changed.

### Approval and review

Changing approved wording now stores the previous revision, clears approval time, changes status to Draft, and writes an audit atomically. Reports exclude it until explicit reapproval. Rejection also clears current approval time. An unchanged normalized Save leaves approval intact.

Quality correction now validates target ownership, validates/parses the replacement, and commits answer update, correction history, issue resolution, and both audit events together. Tests fail the issue update and separately the final audit insert; both roll back the entire operation. Response-level issues with no question cannot be used to correct an arbitrary answer; they still support applicable accept/defer/exclude actions.

### Archived studies

Write attempts are rejected for study edits, forms, submissions, import, correction, quality scanning/review, version creation/restore, persisted analysis/AI turns, findings, standalone audits, and report export. Viewing studies/forms/responses/datasets/issues/versions/analysis history/findings/audits, freshness checks, and report composition remain available. Report export is blocked because it records a study audit event.

### Active-version freshness

`VersionService.freshness(studyId)` compares response membership/status and present answer values with the active version using one consistent SQLite read snapshot. It returns:

| State | Meaning |
|---|---|
| `NO_ACTIVE_VERSION` | No active version exists |
| `CURRENT` | Live membership, statuses, and answers match the active version |
| `STALE` | At least one of those differs |
| `UNKNOWN_LEGACY` | Old snapshot membership is incomplete, so full equality cannot be established |

The check does not create versions. Default-active analyses continue using the saved version and now add an evidence warning if live data differs. Creating a new version restores freshness. The existing auto-baseline behavior when no version exists is preserved for writable studies.

## 6. Tests added

[DataIntegrityRegressionTest](../src/test/java/researchflow/persistence/DataIntegrityRegressionTest.java):

1. `restoreRemovesLaterResponseFromEveryLiveReaderAndCanRestoreItAgain`
2. `restoreMakesLaterAddedAnswerMissingWithoutLosingItsCorrectionHistory`
3. `blankResponseCountsInSnapshotPopulationMissingCountsAndWarnings`
4. `restorePreservesBlankResponseExclusionInBothDirections`
5. `emptyVersionRestoresAnEmptyDataset`
6. `restoreFailureRollsBackMembershipAnswersActiveVersionAndAudit`
7. `restoreKeepsScientificNumbersAndLiteralBackslashesExact`
8. `freshnessTracksCorrectionsMembershipAndExclusionWithoutCreatingVersions`
9. `approvedFindingEditsPreserveRevisionAndRequireReapprovalForReports`
10. `unchangedFindingSaveKeepsApprovalAndRejectionClearsApprovalTimestamp`
11. `findingEditAuditFailureRollsBackTextRevisionAndApproval`
12. `qualityResolutionFailureRollsBackAnswerHistoryIssueAndBothAudits`
13. `successfulQualityCorrectionCommitsAnswerHistoryResolutionAndBothAudits`
14. `finalQualityAuditFailureAlsoRollsBackTheWholeCorrection`
15. `restoringAnotherStudysVersionIsRejectedWithoutChangingEitherDataset`
16. `mismatchedReviewTargetsRejectWithoutAnyMutation`
17. `responseLevelIssueCannotBeCorrectedAgainstAnArbitraryQuestion`
18. `archivedStudyRejectsEveryServiceMutationAndKeepsReadOperationsAvailable` — checks 23 named mutation paths and read access.

[IntegrityMigrationTest](../src/test/java/researchflow/persistence/IntegrityMigrationTest.java):

19. `upgradesV004WithoutInventingHistoricalBlankMembershipOrLosingLiveData`
20. `migrationFailureRollsBackSchemaChangesAndCanBeRetried`

Existing test expectations were preserved except the migration total/table inventory, which must reflect the new schema. Constructor fixture changes supply the new mandatory guard. During development, new-test fixture/format expectations were corrected, including a random-UUID search collision; none required weakening the intended data-integrity assertions.

## 7. Test results

Targeted run: **28 tests passed**, covering the new regressions plus existing migration/version/quality/finding tests.

Final complete command:

```powershell
mvn -o "-Dmaven.repo.local=D:\research_flow\.tools\m2" clean test
```

| Metric | Result |
|---|---:|
| Total | 119 |
| Passed | 119 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| New tests | 20 |
| Clean build performed | Yes |

Maven deleted `target`, compiled 171 main source files and 34 test source files, and completed successfully on JDK 26 using the workspace dependency cache. The run did not contact a real model. SQLite emitted its existing native-access warning; it did not cause test failures.

## 8. Remaining risks and scope limits

- **Irrecoverable legacy membership:** V001–V004 snapshots did not store blank-response membership or its exclusion state. Historical exact restore is intentionally rejected instead of inventing data. Stored results remain readable; corrected complete-denominator guarantees apply to V005 snapshots.
- **Legacy result corrections:** results previously computed using incomplete populations or the old numeric/string decoder are not silently rewritten. New analysis runs use the corrected decoder and flag legacy membership limitations.
- **Retention cost:** absent live records, full snapshots, and wording revisions are retained and can increase database size. No purge/compaction policy or backup feature was added.
- **Version scope:** versions capture the requested dataset membership/answers/exclusion state, not study metadata, form design, or historical quality-review decisions. Restoring values does not rewind the quality-issue timeline; researchers can rescan the restored live dataset.
- **Multi-step operations:** bulk review remains per-command, imports remain per-row, AI turns span model calls and multiple writes, and report file export/audit remain separate operations. Earlier completed work is not undone if a later step fails or a study is archived mid-operation. Each newly attempted database write rechecks the archive policy.
- **Freshness is observational:** equality is checked at call time; it is not a continuously updating subscription or a database backup. The comparison currently loads maps and was not performance-benchmarked.
- **UI verification:** JavaFX source compiled, but no automated desktop suite or manual GUI walkthrough was run. No real model, cross-platform installer, or large-data benchmark was exercised.
- **Historical revision presentation:** finding wording revisions are persisted locally; a revision-history UI was not added.

## 9. Acceptance checklist

- [x] Restoring a complete dataset version reproduces its intended response membership; incomplete legacy versions are explicitly rejected.
- [x] Later-added answers no longer appear after restoring a version where they were absent.
- [x] Blank responses survive new dataset snapshots.
- [x] New snapshot-based analysis includes blank responses in eligible populations and missing counts.
- [x] Editing approved wording requires reapproval before report inclusion.
- [x] Quality correction and issue resolution are atomic, including histories and audits.
- [x] Archived studies reject child-workflow mutations through the service layer.
- [x] Quality-review commands cannot redirect a correction or exclusion to unrelated targets.
- [x] Existing valid workflows remain covered and passing, subject to the explicit legacy-restore restriction.
- [x] Active-version freshness is exposed without silently creating versions.
- [x] The complete automated suite passes after a clean build.
