# Backup, recovery, and release preparation

## Database recovery

Use **Backup / Recovery → Create database backup** and select a separate `.db` file in an existing writable directory. ResearchFlow uses SQLite's online backup API, validates the staged database, then publishes it with an atomic filesystem move. Do not copy the live database file yourself while the application is running: committed data may still be in its WAL.

To recover, open **Backup / Recovery → Restore database backup**, select the saved database and confirm replacement of all studies. Navigation to recovery cancels the previous workspace's tasks. Final recovery waits for active application operations and connections to finish. Navigation is disabled during restoration. After success, reopen studies; previous view objects are discarded.

The application validates SQLite integrity, foreign keys, supported migration history, and required tables. It migrates a staged copy and verifies current schema columns before touching the live database. V005 backups are upgraded to V006 on this copy; the source backup is unchanged. Unknown future migration histories and invalid databases are rejected. Exact dataset restoration of incomplete pre-V005 snapshots remains prohibited after recovery.

Before final restoration, a safety backup named `researchflow-before-recovery-<timestamp>.db` is saved beside the live database. Keep this until recovered studies, responses, snapshots, and findings have been checked. To undo a successful recovery, restore that safety backup through the same workflow. A corrupt file, failed validation, migration error, or failed safety backup leaves the live database intact. SQLite's destination transaction controls final restore; no raw file replacement is used for the active database.

Close other applications using this database before restoration. Separate ResearchFlow processes do not share the application's operation gate. Keep backups on independent storage, test recovery periodically, and retain enough free space for the source, staging, safety backup, and active database. Backups contain the full local research database and should be stored with appropriate access controls. A backup is not encrypted or a database-version snapshot.

If normal startup cannot open the live database, do not delete it. Launch ResearchFlow with `RESEARCHFLOW_DB` pointing to a new file in a writable directory and `RESEARCHFLOW_SEED=false`, then restore the backup there. Verify recovered data before changing your normal database configuration. Preserve the original database and its WAL/SHM files for investigation.

## Build and launch

Install a **JDK 25 or newer** and set `JAVA_HOME`. JDK 25 is the CI baseline; local verification used Oracle JDK 26.0.2.1 on Windows. The official Maven Wrapper 3.3.4 downloads pinned Maven 3.9.11. An internet connection is needed for the first dependency download. Subsequent cached builds can use `-o`.

```powershell
.\mvnw.cmd -B -ntp clean test
.\mvnw.cmd javafx:run
```

On Linux/macOS, use `bash mvnw` in place of `.\mvnw.cmd`. CI runs `clean verify` on Windows and Linux with JDK 25. No real AI model is contacted by the tests. CI is configured but has not been executed remotely in this task.

On this workspace, the cached verification command is:

```powershell
$env:MAVEN_USER_HOME = "D:\research_flow\.tools\wrapper-home"
.\mvnw.cmd -o "-Dmaven.repo.local=D:\research_flow\.tools\m2" clean test
```

If Maven reports `PKIX path building failed`, repair the JDK trust store or use your organization's approved trust configuration. Do not disable certificate validation. This Windows environment successfully downloaded packaging dependencies using `-Djavax.net.ssl.trustStoreType=Windows-ROOT -Djavax.net.ssl.trustStore=NONE` outside the sandbox.

## Runtime image

On Windows with JDK 25+ and `jpackage` on PATH:

```powershell
.\scripts\package-desktop.ps1
```

The script runs a clean tested build with the `desktop` Maven profile, copies runtime dependencies, and produces `target/desktop/ResearchFlow/ResearchFlow.exe` with a bundled Java runtime. No installed Java runtime is required on the recipient machine. Build separately on each supported operating system; JavaFX native libraries are platform-specific. Use a destination without an existing image. The script does not remove an existing distribution.

Set `RESEARCHFLOW_DB` and `RESEARCHFLOW_LOGS` to writable absolute paths before launch, particularly when distributing from a read-only installation directory. The image disables development seeding by default. Preserve data outside `target`, because `clean` removes build output. The application image is not an MSI/EXE installer; signing, installer integration, OS-specific smoke tests, and update distribution remain release work.

## Configuration and diagnostics

Startup validates database-file and log-directory paths, directory writability, HTTP(S) AI endpoints without embedded credentials/query/fragment, model identifiers, boolean flags, and integer timeouts from 1 to 3600 seconds. Errors identify the setting to fix; invalid credential-bearing URLs are not echoed. Blank model configuration means automatic installed-model selection, not a guaranteed model name. Hover over the application brand for the configured endpoint, model policy, and timeout. AI availability and the selected installed model are still determined when contacting the runtime.

## Partial operations and evidence

CSV import commits setup and each response separately. Its outcome reports total, imported, invalid/skipped, failed, and unprocessed rows, setup status, and COMPLETE/FAILED/CANCELLED state. Use the import Cancel button to receive a partial-result receipt, then save the outcome/error details from the result dialog. Previously committed rows remain. Preview still loads the CSV into memory; large-file streaming is future work. Navigating away cancels the workspace and suppresses its callbacks; export the receipt before leaving if needed.

HTML export verifies a temporary file, computes SHA-256, and atomically publishes it before attempting the audit event. If audit persistence fails, the export result explicitly confirms the saved file and supplies a warning. Do not retry blindly and interpret a second audit as evidence of the first operation. Filesystems without atomic-move support fail publication rather than falling back to an unsafe overwrite.

Historical analyses reopen from their saved results, warnings, filters, labels, sample sizes, and version references. V006 records preserve original labels; readable legacy records show question identifiers with an explicit notice. Missing or malformed evidence is shown as unsupported with raw details; no statistics are invented. Reports distinguish the live summary, active snapshot freshness, and historical finding versions. For an older, non-active version, the report explicitly states that live equality has not been evaluated.

## Desktop acceptance before distribution

Use a temporary database, with development seeding disabled. Walk through creating a study/form, activation, submission and import, dataset inspection/correction, quality review, snapshot creation, all five analyses, historical charts, finding approval, export, and restart. Also check import cancellation, navigating away during AI/analysis work, recovery confirmation and navigation locking, archived-study controls, export audit warnings, invalid configuration, high-DPI layout, and packaged launch on a clean machine. Automated integration coverage exercises the service/persistence workflow; these JavaFX interactions have not been manually verified in this task.
