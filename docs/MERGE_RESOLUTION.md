# Merge resolution: origin/main into prottoy

The merge combines `prottoy` at `25979d7` with the incoming main tip `1890958`.
No commit or push was made by the assistant.

## Preserved and integrated features

- Main's 150-response deterministic development seed, including screen-time data, remains. Tests verify its size, idempotence, and embedded quality examples.
- Main's saved-draft form fix deletes the old questions before rebuilding sections, while retaining prottoy's in-transaction study write guard and lifecycle-only structure protection. Its new regression test is preserved.
- Main's progress indicators and per-action Cancel controls are integrated into manual analysis, AI chat, quality scanning, and report export.
- `BusyState` accepts both main's `Future<?>` and prottoy's `Async.TaskHandle`. Scoped cancellation suppresses old callbacks, and a cancelled action can be retried while its worker unwinds. Previously committed results/files are not represented as rolled back by cancellation.
- Main's full-lifecycle acceptance, analysis failure-path, and quality-review failure-path tests remain. Fixtures use the current guarded service constructors and report provenance dependency.
- Prottoy's conversational chat, statistical breakdown, saved/cleared chat history, dashboard diagrams, broader dataset imports, collection links, and design-pattern refactoring remain.
- Prottoy's V005/V006 migrations, exact snapshot membership, historical evidence, archive write guards, atomic correction/review, finding reapproval, recovery gate, and atomic report receipts remain compatible across interfaces, services, repositories, and screens.

## Review method

All 137 unmerged paths were inventoried and their two stage versions inspected. 77 paths were equivalent after ignoring Java comments/formatting (string literals were retained during comparison), or differed only in formatting. The incoming cleanup was retained for those paths. Remaining diffs were reviewed by subsystem rather than resolved by a global ours/theirs choice.

The auto-merged files were also checked for executable changes: these were the seed, form-rebuild fix, BusyState, and the new tests. Existing non-conflicted changes and branch-only files were retained.

For files where main still had the earlier service or persistence contract, the newer implementation was kept so that current features and data integrity were not removed. In particular, the newer strict JSON escaping path remains rather than replacing it with main's older shared escaping helper.

Temporary comparison copies and build logs are in the ignored `.tools/merge-review/` directory. They are not part of the staged merge.

## Verification

The initial combined suite passed 170 tests. Final `clean verify` passed **173 tests, 0 failures, 0 errors, 0 skipped** and built `target/researchflow-ai-0.1.0-SNAPSHOT.jar`. This includes the additional BusyState and cancel/retry tests.

```powershell
.\.tools\maven\apache-maven-3.9.16\bin\mvn.cmd -o "-Dmaven.repo.local=.tools/m2" clean verify
```

Git's unmerged-path list and tracked-file conflict-marker scan are checked after staging. Staging marks resolutions; it does not create the merge commit.

## Per-conflict disposition

| File | Resolution |
| --- | --- |
| `README.md` | Retained newer documentation/theme coverage; added merge integration notes. |
| `docs/README.md` | Retained newer documentation/theme coverage; added merge integration notes. |
| `docs/architecture/CURRENT_IMPLEMENTED_ARCHITECTURE.md` | Retained newer documentation/theme coverage; added merge integration notes. |
| `src/main/java/researchflow/ai/AiPromptBuilder.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/ai/AnalysisPlanParser.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/ai/DisabledLlmClient.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/ai/LlmClient.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/ai/LlmException.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/ai/LocalLlmClient.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/ai/SimpleJson.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/analysis/AnalysisContext.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/analysis/AnalysisData.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/analysis/AnalysisPlanValidator.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/analysis/AnalysisResults.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/analysis/AnalysisStrategy.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/analysis/AnalysisStrategyRegistry.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/analysis/AnalysisSupport.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/analysis/AnalysisWarnings.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/analysis/CorrelationStrategy.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/analysis/CrossTabulationStrategy.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/analysis/FrequencyStrategy.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/analysis/GroupComparisonStrategy.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/analysis/NumericSummaryStrategy.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/analysis/Statistics.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/app/AppConfig.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/app/AppServices.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/command/ReviewCommand.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/dataimport/ColumnTypeInference.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/dataimport/CsvDocument.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/dataimport/CsvParser.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/dataimport/ImportColumnPlan.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/dataimport/ImportException.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/dataimport/ImportPlanner.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/dataimport/ImportPreview.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/dataimport/ImportResult.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/dataimport/ImportRowError.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/dataimport/VariableKeys.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/AiAnswer.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/AnalysisFilter.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/AnalysisMethod.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/AnalysisPlan.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/domain/AnalysisResult.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/AnalysisSummary.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/ChatMessage.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/ChatRole.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/DatasetVersion.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/domain/EvidenceBundle.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/Finding.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/FindingStatus.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/ModelMetadata.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/QualityIssue.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/QualityIssueStatus.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/QualityIssueType.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/QualitySeverity.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/domain/ReportDocument.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/domain/StoredAnalysis.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/domain/VersionAnswer.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/persistence/AnalysisRepository.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/persistence/AuditRepository.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/persistence/ChatRepository.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/persistence/ConnectionFactory.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/persistence/FindingRepository.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/persistence/JdbcAnalysisRepository.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/persistence/JdbcChatRepository.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/persistence/JdbcDatasetRepository.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/persistence/JdbcFindingRepository.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/persistence/JdbcQualityRepository.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/persistence/JdbcResponseRepository.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/persistence/JdbcVersionRepository.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/persistence/MigrationRunner.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/persistence/QualityRepository.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/persistence/ResponseRepository.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/persistence/SnapshotJson.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/persistence/VersionRepository.java` | Retained newer compatible persistence/domain contract and integrity fixes. |
| `src/main/java/researchflow/quality/DuplicateResponseHandler.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/quality/FastSubmissionHandler.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/quality/InvalidRangeHandler.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/quality/MissingRequiredHandler.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/quality/OutlierHandler.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/quality/QualityHandler.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/quality/QualityHandlerChain.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/quality/QualityScanContext.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/report/ReportHtmlRenderer.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/service/AnalysisFacade.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/service/AnalysisService.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/service/AuditService.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/service/DatasetImportService.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/service/FindingService.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/service/QualityReviewService.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/service/QualityService.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/service/ReportService.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/service/VersionService.java` | Retained newer feature-complete implementation after comparing incoming counterpart. |
| `src/main/java/researchflow/ui/AnalysisHistoryView.java` | Retained newer compatible UI features; no independent incoming feature discarded. |
| `src/main/java/researchflow/ui/AnalysisRunView.java` | Integrated main progress/cancel with prottoy features and scoped task lifecycle. |
| `src/main/java/researchflow/ui/AnalysisWorkspaceView.java` | Retained newer compatible UI features; no independent incoming feature discarded. |
| `src/main/java/researchflow/ui/AskYourDataView.java` | Integrated main progress/cancel with prottoy features and scoped task lifecycle. |
| `src/main/java/researchflow/ui/Async.java` | Integrated main progress/cancel with prottoy features and scoped task lifecycle. |
| `src/main/java/researchflow/ui/ChartView.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/ui/DatasetVersionsView.java` | Retained newer compatible UI features; no independent incoming feature discarded. |
| `src/main/java/researchflow/ui/EvidenceActions.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/ui/EvidenceView.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/ui/FindingEditorDialog.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/ui/FindingsReportWorkspaceView.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/ui/FindingsWorkspaceView.java` | Retained newer compatible UI features; no independent incoming feature discarded. |
| `src/main/java/researchflow/ui/ImportWorkspaceView.java` | Retained newer compatible UI features; no independent incoming feature discarded. |
| `src/main/java/researchflow/ui/Navigator.java` | Retained newer compatible UI features; no independent incoming feature discarded. |
| `src/main/java/researchflow/ui/QualityIssuesView.java` | Integrated main progress/cancel with prottoy features and scoped task lifecycle. |
| `src/main/java/researchflow/ui/QualityWorkspaceView.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/ui/ReasonDialog.java` | Retained newer compatible UI features; no independent incoming feature discarded. |
| `src/main/java/researchflow/ui/ReportWorkspaceView.java` | Integrated main progress/cancel with prottoy features and scoped task lifecycle. |
| `src/main/java/researchflow/visualization/ChartBuilder.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/visualization/ChartSpec.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/java/researchflow/visualization/SvgChartRenderer.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/main/resources/css/theme.css` | Retained newer documentation/theme coverage; added merge integration notes. |
| `src/test/java/researchflow/ai/AiPromptBuilderTest.java` | Retained superset of regression assertions and current service contracts. |
| `src/test/java/researchflow/ai/AnalysisPlanParserTest.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/test/java/researchflow/ai/FakeLlmClient.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/test/java/researchflow/ai/LocalLlmClientTest.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/test/java/researchflow/analysis/AnalysisPlanValidatorTest.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/test/java/researchflow/analysis/AnalysisStrategiesTest.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/test/java/researchflow/analysis/StatisticsTest.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/test/java/researchflow/dataimport/ColumnTypeInferenceTest.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/test/java/researchflow/dataimport/CsvParserTest.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/test/java/researchflow/dataimport/ImportPlannerTest.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/test/java/researchflow/dataimport/VariableKeysTest.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/test/java/researchflow/persistence/AnalysisFacadeTest.java` | Retained superset of regression assertions and current service contracts. |
| `src/test/java/researchflow/persistence/DatasetImportServiceTest.java` | Retained superset of regression assertions and current service contracts. |
| `src/test/java/researchflow/persistence/JdbcAnalysisRepositoryTest.java` | Retained superset of regression assertions and current service contracts. |
| `src/test/java/researchflow/persistence/JdbcFindingRepositoryTest.java` | Retained superset of regression assertions and current service contracts. |
| `src/test/java/researchflow/persistence/JdbcQualityRepositoryTest.java` | Retained superset of regression assertions and current service contracts. |
| `src/test/java/researchflow/persistence/JdbcVersionRepositoryTest.java` | Retained superset of regression assertions and current service contracts. |
| `src/test/java/researchflow/persistence/MigrationRunnerTest.java` | Retained superset of regression assertions and current service contracts. |
| `src/test/java/researchflow/persistence/ReportServiceTest.java` | Retained superset of regression assertions and current service contracts. |
| `src/test/java/researchflow/persistence/SeederQualityScanTest.java` | Retained guarded fixture; added 150-row, screen-time and seed-idempotence checks. |
| `src/test/java/researchflow/quality/QualityHandlerChainTest.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/test/java/researchflow/visualization/ChartBuilderTest.java` | Equivalent executable code / formatting; retained main cleanup. |
| `src/test/java/researchflow/visualization/SvgChartRendererTest.java` | Equivalent executable code / formatting; retained main cleanup. |
