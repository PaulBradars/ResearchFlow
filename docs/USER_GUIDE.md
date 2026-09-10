# ResearchFlow AI: Features and User Guide

Guide for the application after the P1 integrity and P2 reliability updates, 9 September 2026.

This guide explains the available features, what each main option does, and how to complete a research workflow. Button names follow the current application source. It is a source-checked operating guide; an interactive desktop walkthrough has not yet been completed.

## Contents

1. [What the software does](#1-what-the-software-does)
2. [Starting the software](#2-starting-the-software)
3. [Navigation and basic operation](#3-navigation-and-basic-operation)
4. [Studies and dashboard](#4-studies-and-dashboard)
5. [Designing forms](#5-designing-forms)
6. [Collecting and viewing responses](#6-collecting-and-viewing-responses)
7. [Importing datasets](#7-importing-datasets)
8. [Inspecting and correcting the dataset](#8-inspecting-and-correcting-the-dataset)
9. [Quality review](#9-quality-review)
10. [Dataset versions](#10-dataset-versions)
11. [Manual analysis and charts](#11-manual-analysis-and-charts)
12. [Ask Your Data](#12-ask-your-data)
13. [Reopening analysis history](#13-reopening-analysis-history)
14. [Creating and approving findings](#14-creating-and-approving-findings)
15. [Previewing and exporting reports](#15-previewing-and-exporting-reports)
16. [Database backup and recovery](#16-database-backup-and-recovery)
17. [A complete practice workflow](#17-a-complete-practice-workflow)
18. [Configuration and troubleshooting](#18-configuration-and-troubleshooting)
19. [Limits and everyday checklist](#19-limits-and-everyday-checklist)

## 1. What the software does

ResearchFlow AI is a desktop workspace for collecting research data, reviewing its quality, running supported analyses, recording reviewed findings, and producing an HTML report. Studies and their saved records are held in a local SQLite database. Manual collection, cleaning, analysis, reporting, and backup do not require an AI model.

| Feature | What you can do | Where to find it |
|---|---|---|
| Study management | Create studies, record objectives and research questions, edit metadata, archive completed studies | Studies |
| Study overview | See forms, responses, open issues, versions, analyses, and approved-finding counts | Dashboard |
| Form design | Create a draft, add typed questions, reorder questions, preview, activate, and close collection | Form |
| Local collection | Enter and validate one response at a time | Form → Collect response |
| Response overview | See submission time, number of answers, and collection duration when available | Responses |
| Dataset import | Create a form and responses from CSV, TSV, JSON, or Excel, review inferred types, and inspect partial outcomes | Import |
| Dataset inspection | Search, filter, sort, page through answers, and open response details | Dataset |
| Corrections | Replace a value with a documented reason and retained history | Dataset → Response details → Correct |
| Audit history | Review recorded activity and correction events | Dataset → Audit timeline |
| Quality checks | Detect missing required values, invalid ranges, duplicates, simple outliers, and fast submissions | Quality / Versions → Issues |
| Quality decisions | Correct, accept, defer, or exclude an affected response | Issues → Review selected |
| Dataset snapshots | Save a baseline and restore a complete earlier dataset | Quality / Versions → Versions |
| Analysis | Run frequency, numeric summary, Pearson correlation, cross-tabulation, or two-group comparison | Analysis → Run analysis |
| AI assistance | Ask a supported analysis question in plain language and review evidence with an explanation | Analysis → Ask Your Data |
| Historical evidence | Reopen saved results after restart, view available charts, and draft findings | Analysis → History |
| Finding review | Save wording, approve or reject a finding, and view its attached chart | Findings / Report → Findings |
| Report generation | Preview a study report and export approved findings to HTML | Findings / Report → Report |
| Database recovery | Back up or restore the entire database | Top bar → Backup / Recovery |
| Task control | Request cancellation and leave the current workspace | Top bar → Cancel workspace tasks |

### Important terms

| Term | Meaning |
|---|---|
| Study | A research project containing its forms, responses, versions, analyses, and findings |
| Form | A questionnaire; a study can contain multiple forms |
| Variable key | A short identifier for a question, such as `sleep_hours` |
| Response | One submitted set of answers; optional questions can be blank |
| Live dataset | The responses, answer values, and exclusions currently in effect |
| Dataset version / snapshot | A saved dataset state used as a reproducible analysis baseline |
| Active version | The saved version used by default for new analyses; it can differ from live data |
| Evidence | The saved analysis result, method, variables, filters, sample size, warnings, and dataset-version reference |
| Finding | A written statement linked to evidence, with a review status |
| Database backup | A separate file containing the whole database, across all studies |

## 2. Starting the software

### Run from the source project

Install a JDK 25 or newer and make sure `java -version` reports the intended JDK. In PowerShell, open the project directory and run:

```powershell
cd D:\research_flow
.\mvnw.cmd clean test
.\mvnw.cmd javafx:run
```

The test command is useful for the first setup; you do not need to run it every time you open the software. The Wrapper downloads Maven and dependencies on first use. On Linux/macOS, replace `.\mvnw.cmd` with `bash mvnw`.

By default, development launch uses `data/researchflow.db`, writes logs under `logs/`, and can seed a demo study when the database is empty. To start your own workspace without demo seeding:

```powershell
$env:RESEARCHFLOW_SEED = "false"
.\mvnw.cmd javafx:run
```

### Run a packaged Windows copy

If you have a generated Windows application image, open `ResearchFlow.exe` in its `ResearchFlow` folder. Keep its `app` and `runtime` folders with the executable. It includes a Java runtime; do not distribute only the `.exe`.

For this workspace, the generated image is under `.tools/desktop/ResearchFlow/`. The standard packaging script produces an image under `target/desktop/ResearchFlow/`. Packaging succeeded, but interactive launch on a clean machine remains to be verified.

Use a writable database location outside build/output folders. Set `RESEARCHFLOW_DB` and `RESEARCHFLOW_LOGS` if you need explicit locations; see [configuration](#18-configuration-and-troubleshooting). Reopen with the same database path to see your existing studies.

## 3. Navigation and basic operation

The application opens on **Studies**. Double-click a study row to open it. The left navigation then shows its workspaces.

| Option | What it does |
|---|---|
| All studies | Returns to the study list |
| Dashboard | Opens the current study's overview |
| Form | Opens questionnaire design and collection |
| Responses | Lists submitted responses |
| Import | Opens dataset import |
| Dataset | Opens answer inspection, correction, and the audit timeline |
| Quality / Versions | Opens the Issues and Versions tabs |
| Analysis | Opens Run analysis, Ask Your Data, and History tabs |
| Findings / Report | Opens the Findings and Report tabs |
| Backup / Recovery | Opens whole-database backup and restore |
| Cancel workspace tasks | Requests cancellation of current workspace tasks and returns to Studies |
| Hover over ResearchFlow AI in the top bar | Shows configured AI endpoint, model-selection policy, and timeout |

In dialogs, **OK** confirms entered values, **Cancel** abandons the dialog, and **Close** dismisses it. Read validation messages if confirmation does not proceed. A button may require a selected table row, an appropriate status, or completion of a background task.

There is no single global Save button. Individual actions such as submitting a response, saving wording, or creating a snapshot persist their own changes. Unsaved dialog input is not guaranteed to survive navigation.

Changing workspaces cancels the previous workspace's pending tasks and suppresses its later callbacks. Cancellation is a request: already committed rows and already published files remain. During import, prefer **Cancel import** if you want to receive and save the partial-result receipt before leaving.

## 4. Studies and dashboard

### Studies options

| Option | What it does |
|---|---|
| New study | Creates a study and opens its dashboard |
| Edit | Changes the selected study's metadata |
| Archive | After confirmation, marks the selected study read-only while retaining its records |
| Show archived | Includes archived studies in the list |
| Double-click a study | Opens the study |

### Creating or editing a study

1. Select **New study**, or select an existing row and choose **Edit**.
2. Fill in the fields below.
3. Select **OK** and wait for the study to open or the list to refresh.

| Field | What to enter |
|---|---|
| Title * | A required project name, such as `Student Sleep and Focus` |
| Researcher | Researcher or project-owner name |
| Description | Background and purpose |
| Objectives | What you intend to investigate |
| Research questions | One research question per line |
| Start date | Optional study start date; a new study initially suggests today |
| End date | Optional end date; it cannot precede the start date |

The **Dashboard** displays study information and summary cards for forms, responses, open quality issues, dataset versions, analyses, and approved findings. These are overview counts, not the sample size of any particular historical analysis.

### Archiving

Archive only when you intend the study to become read-only. You can still open records, inspect saved evidence, and compose a report preview. New submissions, imports, corrections, quality scans/reviews, snapshots/restores, analyses/AI turns, finding changes, and report export are rejected. Export is a write operation because it records an audit event.

There is currently no **Unarchive** button. Some controls may still appear enabled on an archived study, but the service rejects the mutation. Archiving is not deletion and is not a backup.

## 5. Designing forms

Open **Form → New form**, enter a title and optional description, and select **OK**. Select the form in the list to work with it.

| Option | What it does | Availability |
|---|---|---|
| New form | Creates another questionnaire in this study | Writable study |
| Add question | Opens the question editor | Draft form |
| Edit | Edits the selected question, not study metadata | Draft form with a selected question |
| Delete | Removes the selected question from the draft | Draft form with a selected question |
| Move up / Move down | Changes the selected question's order | Draft form with a selected question |
| Preview | Shows the respondent controls without saving a response | Form with questions |
| Activate | Validates the draft and opens it for collection | Draft form |
| Collect response | Opens local respondent entry | Active form |
| Close form | Ends collection for this form | Active form |

The form lifecycle is **Draft → Active → Closed**. Structure changes are permitted in Draft. After activation, the form's questions are locked; design and preview them first. The current UI has no reopen-closed-form or return-to-draft action. Create a new form if you need a different questionnaire.

### Question editor options

| Field | What it does |
|---|---|
| Variable key | Identifies the question in the dataset. Start with a letter, then use letters, digits, or underscores; maximum 64 characters. Use distinct keys within the form. |
| Question | The wording displayed to the respondent |
| Help text | Optional instructions displayed with the question |
| Type | Chooses the kind of answer and its input control |
| Required | Prevents submission without an answer to this question |
| Minimum / Maximum | Optional numeric bounds; maximum must be at least minimum |
| Options | One choice per line; choice, Likert, and rating types require at least two options |

### Supported question types

| Type | How respondents answer | Example |
|---|---|---|
| Short text | Type text | A comment or participant code |
| Number | Type a numeric value, subject to configured bounds | Sleep hours, age, score |
| Single choice | Select one option from a dropdown | Group A / Group B |
| Multiple choice | Tick one or more checkboxes | Activities undertaken |
| Yes / No | Select Yes or No | Attended a session |
| Likert | Select one of your configured options | Strongly disagree through strongly agree |
| Rating | Select one of your configured options | Poor / Fair / Good, or 1 through 5 |
| Date | Choose a date | Observation date |

**Choose Number for variables you want to average or correlate.** Likert and Rating currently behave as categorical choices, even when their labels look numeric. The application does not automatically convert them into numeric scores.

## 6. Collecting and viewing responses

1. Open **Form** and select an active form.
2. Select **Collect response**.
3. Enter the answers. A `*` marks a required question.
4. Select **Submit response**.
5. Correct any displayed validation errors and submit again.
6. After success, inspect **Responses** or **Dataset**. For another response, return to the form and choose **Collect response** again.

**Preview** does not submit data. Collection is local to the desktop application; this is not a public survey link or a mobile collection portal.

The **Responses** table shows the form, submission timestamp, answer count, and duration in seconds when available. A dash means no duration was recorded; imported responses need not have a collection duration. To inspect actual values or correct them, use **Dataset → Response details**.

A response can contain no answers when its questions are optional. Complete dataset snapshots retain such responses so missing-data counts can represent them.

## 7. Importing datasets

Use **Import** to create a **new form and new responses** from a CSV, TSV, JSON, XLSX, or XLS file. It does not append into an existing form or automatically match/deduplicate prior imports.

### Preparing the file

For CSV or TSV, use UTF-8 text with a header row and one subsequent row per response. TSV uses tabs between columns. Quote values containing delimiters or line breaks. Use simple numeric values for numbers and `YYYY-MM-DD` for dates.

For Excel (`.xlsx` or `.xls`), choose a worksheet from the **Worksheet (Excel)** selector. The first non-empty row becomes headers; subsequent non-empty rows become responses. Date cells become ISO dates. Formula cells use their saved results: recalculate and save the workbook in Excel first. Fix Excel error cells before importing. Password-protected workbooks are not supported.

For JSON, use an array of flat objects, for example:

```json
[{"participant":"P01","age":24},{"participant":"P02","age":null}]
```

A wrapper such as `{"data": [...]}` is also supported. Keys across all rows become columns in first-seen order; missing keys and null values become blank answers. Nested objects and arrays must be flattened first. Choose **Short text** for identifiers or numbers whose exact textual representation must be retained; Number answers use floating-point storage.

Files are limited to 50 MB; JSON additionally has a 10-million-character limit. Excel imports allow up to 100,000 data rows and 1,000 columns per sheet. Import receipt row numbers refer to the parsed table (header is row 1), not original Excel row positions when blank rows were skipped.

```csv
participant,sleep_hours,focus_score,observation_date
P01,6,4,2026-09-01
P02,7.5,7,2026-09-02
P03,,5,2026-09-03
```

### Import options

| Option / field | What it does |
|---|---|
| Choose dataset file... | Reads the file, counts data rows, and proposes a column plan |
| Worksheet (Excel) | Selects the worksheet to preview and import |
| Form title | Names the new form that will hold imported responses |
| Column checkbox | Includes or ignores that source column |
| Column | Shows the original column header |
| Label | Sets the question label for the imported column |
| Type | Lets you choose Short text, Number, or Date; review the inferred choice |
| Required | Treats a blank value in that column as invalid for row submission |
| Import | Creates/activates the new form and processes its rows |
| Cancel import | Requests a stop at a processing boundary and returns a partial-result receipt |
| Save import outcome and errors | Saves the outcome summary and row errors to a text file |

Review every included column before choosing **Import**. At least one must be included, and the form title is required. Variable keys are generated from the headers. Categorical form types such as Single choice and Yes / No are not offered in the current import mapping UI; an imported text grouping column does not become a categorical analysis variable automatically.

### Understanding the outcome

| Reported item | Meaning |
|---|---|
| Total rows | Data rows discovered in the parsed file |
| Imported | Successfully committed responses |
| Invalid/skipped | Rows rejected by input validation |
| Failed | Rows that hit an operational failure during submission |
| Unprocessed | Remaining rows that were never attempted |
| Setup succeeded | Form setup and activation completed |
| COMPLETE | Processing reached the end; invalid/skipped rows may still exist |
| FAILED | An operational or setup failure stopped the import |
| CANCELLED | Cancellation stopped processing |

**Import is not atomic.** A failure can leave a created form and successfully imported rows. Save the receipt, inspect the imported data, and prepare a file containing only rows you still need before retrying. Reimporting the entire file can create duplicates in another form. The quality duplicate check operates within a form, so it is not a substitute for checking repeated imports.

Preview currently loads the file into memory. There is no guaranteed large-file streaming mode. If you need the outcome receipt, wait for it and save it before leaving the workspace.

## 8. Inspecting and correcting the dataset

Open **Dataset**. Each row represents a live response; answer columns use variable keys. Hover over a variable heading for additional information where available.

| Option | What it does |
|---|---|
| Search responses and values | Narrows the grid using response/value text |
| Filter variable | Chooses one variable to test |
| Operator | Chooses how the filter compares values |
| Filter value | Supplies the comparison value, except for is missing |
| Sort | Chooses submission time, duration, or variable ordering |
| Sort variable | Chooses the variable for variable-based sorting |
| Apply | Runs the current search/filter/sort and returns to the first page |
| Clear | Clears search/filter values and restores Newest first |
| Previous / Next | Moves through pages of up to 50 responses |
| Response details | Opens the selected response; double-clicking a row also opens it |
| Audit timeline | Shows up to the newest 250 recorded study events |

### Filter and sort choices

| Operator | Use it to find |
|---|---|
| contains | Values containing the supplied text |
| equals | Values matching the supplied value |
| greater than | Numeric/date values above the supplied value |
| less than | Numeric/date values below the supplied value |
| is missing | Responses without an answer to the selected variable; no value is needed |

Sort choices are **Newest first**, **Oldest first**, **Shortest duration**, **Longest duration**, **Variable ascending**, and **Variable descending**. Select a sort variable for the last two.

Dataset filtering changes the grid you see. It does not delete responses, change exclusions, or automatically carry over to an analysis. Set analysis filters separately.

### Correcting an answer

1. Select a response and open **Response details**.
2. Find the affected variable and select **Correct**.
3. Enter the replacement using its type-specific control.
4. Enter a reason of at least three characters, such as `Corrected against original paper form`.
5. Confirm the dialog and inspect the updated value.

Corrections preserve history and create audit records. The replacement must be valid for the question; the current correction workflow requires a nonblank value. A correction changes live data, not a previously saved analysis or snapshot. Create a new snapshot before analyzing the corrected dataset.

If you correct from Dataset rather than through a quality issue, run the quality scan again to reconcile issue status.

## 9. Quality review

Open **Quality / Versions → Issues** and choose **Scan for quality issues**.

| Detected issue | Meaning |
|---|---|
| Missing required value | A required answer is absent |
| Invalid numeric range | A number falls outside the question's configured bounds |
| Duplicate response | A response matches an earlier full answer set within the same form |
| Simple outlier | A numeric value is flagged by the implemented IQR-based check |
| Unusually fast submission | Recorded completion time is unusually short under the implemented rule |

The table shows type, severity, status, affected response, explanation, and detection time. Click a row once to show its explanation, review note, and available actions below the table. Double-click the row, press Enter, or choose **Review this issue / Review selected** to open its review dialog. A flag requests review; it does not prove that an answer is wrong. Scanning records/reconciles issues and is therefore unavailable for archived studies.

| Option | What it does |
|---|---|
| Status | Filters by Open, Accepted, Deferred, Resolved, or All statuses; Open is selected initially |
| Refresh | Reloads saved issues without running a scan |
| View response | Shows the affected response and its current answers without changing data |
| Type / Severity | Combine issue type and severity criteria with the status filter |
| Search | Matches explanation, response ID, question ID, or review note, ignoring case |
| Clear filters | Shows all saved issues in every status |
| Scan for quality issues | Rechecks the live dataset and preserves the selected filters; count shows matching versus total issues |
| Review selected | Opens the selected issue and applicable actions |
| Correct answer | Replaces the specific affected answer and resolves its issue together; requires an issue with a question target |
| Accept | Records a reason for accepting the issue without changing its answer |
| Defer | Records a reason to postpone the decision |
| Exclude response | Excludes the affected response from eligible analysis populations; retains the record |
| Accept selected / Defer selected / Exclude selected | Applies the corresponding decision to multiple selected issues |

Use Ctrl-click or Shift-click to select multiple rows. Supply a reason or note of 3?1,000 characters and review the confirmation. Number corrections must be finite and within configured bounds; dates use YYYY-MM-DD. Invalid input remains in the editor with an error so you can fix it without re-entering the reason. Bulk decisions are processed independently: a later failure does not undo earlier successful decisions.

**Accepted** means the issue has been reviewed and accepted, not that its data was corrected. **Resolved** records resolution. Open and Deferred issues offer applicable review actions; an issue with no question target cannot be used to correct an arbitrary answer.

After Accept, Defer, or Correct/Exclude, the issue leaves the Open list. Use Accepted, Deferred, Resolved, or All statuses to find it again. Accepted and Resolved issues are read-only; Deferred issues can still be reviewed. Duplicate and fast-submission flags target the whole response, so they do not offer **Correct answer**. Use **View response** to inspect them, then Accept, Defer, or Exclude as appropriate. Run another scan after data changes to resolve other flags whose conditions no longer exist.

There is no general **Undo exclusion** button in this screen. Restoring a complete snapshot made before exclusion can recover its earlier exclusion state, but also restores that snapshot's other dataset values. Save an appropriate snapshot before substantial cleaning decisions.

## 10. Dataset versions

Open **Quality / Versions → Versions**.

| Option / column | What it does or means |
|---|---|
| Create version snapshot | Saves current response membership, answer presence/values, and exclusions as a new active version |
| Reason | Required explanation for the snapshot, at least three characters |
| Change summary | Optional description of the cleaning batch or other changes |
| Restore selected version | Makes live data match a selected complete snapshot and creates a new active version |
| Version | The snapshot number |
| Active | Identifies the default saved version for analysis |
| Parent | Shows the recorded parent version in its lineage |
| Created | Snapshot creation date/time |
| Response membership | Shows Complete or a legacy exact-restore limitation |

### Saving a baseline

After collection or a cleaning batch, select **Create version snapshot**, enter a reason and optional summary, and confirm. For example: `Reviewed imported responses before analysis`.

### Restoring a dataset version

Select a complete version, choose **Restore selected version**, enter a reason, and confirm. Later responses and later-added answers cease to appear in the live dataset if they were absent from that snapshot. Physical records and saved snapshots remain retained. A later complete saved version can be restored again.

Restoring the currently active snapshot is allowed because live data may have changed since it was created. Restoration does not rewind study metadata, form design, finding wording, or the historical quality-decision timeline. Rescan the restored live dataset when you need fresh quality issues.

### Freshness and older versions

| State | What it means | What to do |
|---|---|---|
| CURRENT | Live data matches the active snapshot | Analyze that version if it is your intended baseline |
| STALE | Live membership, values, or exclusions differ | Create a new snapshot to include live changes, or deliberately analyze the older snapshot |
| NO_ACTIVE_VERSION | No snapshot exists | Create one; default analysis can also create a baseline automatically |
| UNKNOWN_LEGACY | The old version did not save complete response membership | Create a new baseline from live data |

Freshness appears in report/provenance information and relevant analysis warnings; the Versions table is not a continuously updating freshness dashboard. An explicit historical selection stays historical.

Pre-V005 snapshots can be read but cannot be restored exactly because their blank-response membership is unknown. New snapshots retain blank responses. A dataset snapshot lives inside the same database and does not protect against losing the database file; use a database backup too.

## 11. Manual analysis and charts

Open **Analysis → Run analysis**.

| Option | What it does |
|---|---|
| Method | Selects one of the five implemented analysis methods |
| Primary variable | Selects the main variable or numeric outcome |
| Secondary variable | Selects the second variable for correlation, cross-tabulation, or two-group comparison |
| Filter | Optionally selects one variable, operator, and comparison value for this analysis |
| Dataset version | Selects a saved version, or Active (default) |
| Run analysis | Validates the request, computes results, and saves the analysis/evidence |
| Create finding from this evidence | Opens a draft statement and available chart preview |

To run an analysis, select its method and required variables, optionally set a filter, choose the intended version, and select **Run analysis**. Selecting both a filter variable and operator activates the filter; is missing does not need a value. The current manual panel exposes one filter at a time.

| Method | Select | Result | Available chart |
|---|---|---|---|
| Frequency / percentage | One variable | Counts, percentages, and missing count | Category-count bar chart |
| Numeric summary | One Number variable | Count, missing count, mean, median, standard deviation, minimum, maximum | Histogram |
| Correlation | Two different Number variables | Paired sample size and Pearson's r | Scatter plot |
| Cross-tabulation | Two different categorical variables | Counts for category combinations and total count | Bar chart of category combinations |
| Two-group comparison | Number outcome as primary; categorical group as secondary | Each group's count/mean/SD, mean difference, Welch's t, degrees of freedom, Cohen's d | Group-mean bar chart |

For cross-tabulation and group comparison, supported categorical types are Single choice, Yes / No, Likert, and Rating. Short text and Multiple choice are not supported group variables for these methods. Two-group comparison requires exactly two groups with data and at least two usable responses per group.

Charts are selected automatically, not chosen from a chart-type menu. The immediate manual result panel displays evidence; the finding-creation dialog and supported historical details provide chart views. Histogram/scatter charts need usable values in their saved dataset version.

### Reading evidence correctly

- Check the **dataset version**, selected variables, **filters**, **sample size**, and **warnings** before writing a finding.
- Excluded responses do not contribute to eligible analysis populations. Missing answers can reduce the usable count; correlation uses responses with both numeric answers.
- Frequency percentages use responses with an answer to that variable. Multiple-choice responses contribute to each selected category, so percentages can sum above 100%.
- A small sample or missing-data warning is part of the evidence. The application does not establish that a study design supports a causal claim.
- New live data does not automatically enter an already-active snapshot. Default-active analysis warns when live data has changed; create a new snapshot when you want updated results.
- If no active version exists, a default analysis can create a baseline for a writable study. Re-running an analysis creates another saved analysis rather than updating the old one.

## 12. Ask Your Data

Open **Analysis → Ask Your Data** when a compatible AI runtime is configured and available. With the default configuration, it connects to local Ollama at `http://localhost:11434`. You need a running runtime and an installed model; consult the [setup guide](../README.md) for setup commands.

1. Check the availability message.
2. Enter a question referring to your study variables.
3. Select **Ask** once and wait for processing.
4. Review the response and its structured evidence.
5. Use **Create finding from this evidence** if you want to draft a statement for review.

Examples, assuming the named variables exist:

| Question | Intended supported method |
|---|---|
| What percentage of participants are in each group? | Frequency |
| Summarize sleep hours. | Numeric summary |
| How are sleep hours associated with focus score? | Correlation |
| Show the cross-tabulation of group and attendance. | Cross-tabulation |
| Compare focus score between the two groups. | Two-group comparison |

AI proposes a plan; the application validates its variables and method and computes the statistics using the same implemented analysis pipeline. Unsupported or invalid plans can be rejected. AI does not add new statistical methods or automatically approve findings. Review the evidence even when the explanation sounds confident.

New statistical chat answers include a fuller AI interpretation and an expanded **Computed statistical breakdown** panel. Correlation includes Pearson r with greater precision, direction, r squared, and each variable's mean, median, sample standard deviation, minimum, and maximum on the same paired rows and saved snapshot. Identifier-like labels such as CustomerID receive a caution about interpretation. Constant variables or insufficient pairs are marked not estimable. Numeric summaries, frequencies, cross-tabulations, and group comparisons also include supporting statistics and explanation of their denominators or units; cross-tabulations include row and overall percentages.

The computed breakdown remains available if AI explanation generation fails, and is included in saved/exported chat. Old chat replies retain their original content; rerun the query for the expanded format. P-values, confidence intervals, and automated outlier/distribution checks are not computed by this feature; the AI is instructed not to claim them.

You can also ask everyday questions, request explanations, or ask for app help. These replies do not create statistical evidence. Basic greetings such as “How are you?” work immediately, even without a running model. Other questions use the configured runtime, with the latest 12 chat messages as bounded context for follow-ups. The assistant has no live internet or location access.

Saved chat messages can be reopened with the study; the screen shows the latest 250 messages. **Save chat history** exports the complete conversation as a UTF-8 text file, including timestamps and linked analysis IDs. **Clear chat** asks for confirmation and removes this study's chat messages and conversation context, while keeping analyses and findings. Save a copy before clearing if you need one. Press Enter or click **Ask** to send a message.

If an explanation fails after computation, a saved analysis may still exist; inspect **History** before repeating the request. Manual analysis remains available if AI is disabled, unreachable, or fails.

The endpoint determines where AI requests go. Keep the default local endpoint if you intend to use a local runtime; configuring another host sends requests to that configured service. An automatically selected model is a selection policy, not a promise of a particular model.

## 13. Reopening analysis history

Open **Analysis → History**.

| Option / column | What it does or means |
|---|---|
| Refresh | Reloads saved analyses |
| Method | Analysis type |
| Version | Dataset version used for that run |
| Sample size | The saved analysis sample size |
| Source | Manual or AI-originated analysis |
| Run at | Saved analysis timestamp |
| View details | Opens the selected historical record; double-clicking also opens it |

Supported records reopen as structured evidence with available charts and a finding-creation action. This reads the saved result rather than recalculating it against live data. It works after closing and reopening the application with the same database.

Newer evidence retains the original variable labels. Older readable records may display question identifiers and a **Legacy** notice because labels were not saved. If required historical fields cannot be reconstructed, the view explicitly says so and shows raw details instead of inventing a result. Unsupported evidence does not provide the normal typed finding-drafting action.

Historical provenance distinguishes an older version from the active selection. For older non-active evidence, live equality is explicitly unevaluated; do not interpret its date or presence in History as proof that it matches the current dataset.

## 14. Creating and approving findings

An analysis result becomes a reportable written conclusion only after you create and approve a finding.

### Create a finding

1. Run an analysis or reopen supported evidence in **History**.
2. Select **Create finding from this evidence**.
3. Review the suggested wording and available chart preview.
4. Edit the statement to accurately describe the evidence. Finding text must contain 10–2,000 characters.
5. Select **OK**. The finding is saved as **Draft**.

### Review options

Open **Findings / Report → Findings**, select a row, and choose **Review selected**, or double-click it.

| Option | What it does |
|---|---|
| Status / All statuses | Filters the list to Draft, Approved, Rejected, or all findings |
| Refresh | Reloads the list |
| Review selected | Opens wording, evidence summary, and an attached chart if present |
| Save wording | Persists edited wording and closes the dialog |
| Approve | Marks saved wording approved and eligible for report inclusion |
| Reject | Marks the finding rejected and excludes it from reports |

If you edit a draft's text, **Approve** is disabled until you save. Select **Save wording**, reopen the finding, and then **Approve**.

Changing an approved finding's wording returns it to **Draft**, clears its approval, and retains the previous wording in stored revision history. It stays out of reports until approved again. Saving unchanged normalized wording leaves its approval intact. A revision-history browser is not currently provided.

Editing wording never changes the finding's underlying analysis/version. If you need a conclusion based on newly collected or corrected data, run a new analysis and create a new finding from that evidence.

## 15. Previewing and exporting reports

Open **Findings / Report → Report**.

| Option | What it does |
|---|---|
| Preview | Composes a fresh on-screen summary from the study and approved findings |
| Export HTML… | Composes and saves a self-contained HTML report to your chosen location |

The report includes study information, a live summary, active-version information, current quality counts, approved findings and available charts, evidence provenance, and limitations. Draft and Rejected findings are omitted. The live response count is not automatically the denominator behind every finding.

To export:

1. Finish reviewing and approving the intended findings.
2. Select **Preview** to check report contents.
3. Select **Export HTML…** and choose a file in an existing writable folder.
4. Read the completion message and retain the reported SHA-256 hash if you need an export receipt.
5. Open the saved HTML in a browser to inspect and share it.

| Outcome | Meaning |
|---|---|
| Export complete | A verified file was published and its audit event was recorded |
| File saved; audit incomplete | The HTML exists, but the audit write failed; keep the file and hash receipt |
| Report was not published | No successful new publication was reported; inspect the error and destination before retrying |

Publication uses a verified temporary file and an atomic move where supported. Export does not silently treat an audit failure as absence of the file. A saved report is a document from its export time; it does not update when the study changes. PDF and DOCX export are not included.

## 16. Database backup and recovery

### Snapshot, report, and backup are different

| Item | Scope | Main purpose |
|---|---|---|
| Dataset snapshot | One study's dataset values, membership, and exclusions; inside the database | Repeatable analysis and dataset rollback |
| HTML report | Exported narrative, summaries, approved findings, and charts | Readable research output |
| Database backup | All studies and saved database records in a separate file | Whole-database recovery |

### Create a backup

1. Select **Backup / Recovery** in the top bar.
2. Select **Create database backup**.
3. Choose a separate filename in an existing writable folder.
4. Wait for **Verified backup saved**.
5. Keep a copy on independent storage if you need protection against loss of the working drive.

Use this workflow rather than copying the active `.db` file while the application is writing. SQLite may have committed data in companion WAL files. A backup includes the research data, so choose its storage location accordingly.

### Restore a backup

1. Finish current work and close other database tools using the file.
2. Open **Backup / Recovery → Restore database backup**.
3. Select the backup file.
4. Read the confirmation: this replaces the **entire database**, not just the selected study.
5. Confirm and wait; navigation is disabled during restoration.
6. Note the safety-backup path in the success message.
7. Reopen studies and check recovered responses, versions, and findings.

The application validates the backup, prepares compatible migrations on a staged copy, and saves the previous live database as `researchflow-before-recovery-<timestamp>.db` before final restoration. Invalid files and unsupported schemas are rejected. Keep the safety backup until you are satisfied with recovery. To reverse a successful restore, restore that safety backup through the same workflow.

**Back to studies** leaves the recovery screen. If normal startup cannot open your database, use the separate-database recovery procedure in [Recovery and release instructions](RECOVERY_AND_RELEASE.md#database-recovery). Do not delete your research database to troubleshoot startup.

## 17. A complete practice workflow

Use a new practice study so these actions do not alter real research.

### A. Design and collect

1. Select **New study**. Enter `Practice: Sleep and Focus` and the research question `How are sleep hours associated with focus?`.
2. Open **Form → New form** and name it `Practice questionnaire`.
3. Use **Add question** to create the following questions:

| Variable key | Question | Type | Settings |
|---|---|---|---|
| `sleep_hours` | Hours slept | Number | Required; minimum 0, maximum 24 |
| `focus_score` | Focus score | Number | Required; minimum 0, maximum 10 |
| `group` | Study group | Single choice | Required; options A and B on separate lines |
| `attended` | Attended session | Yes / No | Required |

4. Choose **Preview**, check the inputs, close the preview, and select **Activate**.
5. Use **Collect response** and **Submit response** repeatedly to enter six responses:

| Sleep hours | Focus score | Group | Attended |
|---:|---:|---|---|
| 5 | 3 | A | No |
| 6 | 4 | A | Yes |
| 7 | 6 | A | Yes |
| 6 | 5 | B | No |
| 8 | 8 | B | Yes |
| 9 | 9 | B | No |

### B. Inspect, review, and save a baseline

1. Open **Responses** and confirm six submissions are listed.
2. Open **Dataset**. Try `sleep_hours greater than 6`, then **Apply**, and use **Clear** to return to the full grid.
3. To practice correction, open one response and correct a value using a clearly marked practice reason. Inspect **Audit timeline** afterwards.
4. Open **Quality / Versions → Issues → Scan for quality issues**. Rapid practice entry may produce fast-submission flags. Review any flags; no issues is also a valid result.
5. Open **Versions → Create version snapshot**. Use the reason `Practice dataset reviewed`.

### C. Analyze all five methods

In **Analysis → Run analysis**, choose the saved version and run these separately:

| Method | Primary | Secondary |
|---|---|---|
| Frequency / percentage | Study group | Not needed |
| Numeric summary | Hours slept | Not needed |
| Correlation | Hours slept | Focus score |
| Cross-tabulation | Study group | Attended session |
| Two-group comparison | Focus score | Study group |

These six invented responses are for learning the controls, not drawing a research conclusion. Read the small-sample warnings. If you excluded responses during quality practice, ensure the group comparison still has at least two usable responses in each group.

### D. Create a finding, export, and reopen

1. From a result, select **Create finding from this evidence** and write a practice statement that matches its displayed result.
2. Open **Findings / Report → Findings → Review selected** and **Approve** the saved wording.
3. Open **Report**, select **Preview**, then **Export HTML…**.
4. Open the HTML file and check that the approved finding and its provenance are present.
5. Use **Backup / Recovery → Create database backup**.
6. Close and reopen ResearchFlow using the same database path.
7. Open the practice study and check **Analysis → History** and **Findings**. Open a historical analysis to see that its saved evidence remains available.

For CSV practice, create a separate study and import the CSV example in section 7. Its Number columns support numeric summary and correlation. Use the manually designed categorical form above for cross-tabulation and two-group comparison.

## 18. Configuration and troubleshooting

Configuration is supplied before launch through environment variables or Java system properties, not an in-app Settings screen. Java properties take precedence. PowerShell environment settings below apply to programs launched from that terminal.

| Environment variable | Default | Purpose |
|---|---|---|
| `RESEARCHFLOW_DB` | `data/researchflow.db` | Database file location |
| `RESEARCHFLOW_LOGS` | `logs` | Log directory |
| `RESEARCHFLOW_SEED` | `true` for development; packaged launcher disables it by default | Demo-data seeding |
| `RESEARCHFLOW_AI_ENABLED` | `true` | Enables AI functionality; manual features remain usable when false |
| `RESEARCHFLOW_AI_BASE_URL` | `http://localhost:11434` | Compatible AI runtime endpoint |
| `RESEARCHFLOW_AI_MODEL` | Blank | Automatic installed-model selection; set an installed model identifier to choose explicitly |
| `RESEARCHFLOW_AI_TIMEOUT_SECONDS` | `60` | Request timeout, an integer from 1 through 3600 |

For example, configure an existing writable research directory before launch:

```powershell
$env:RESEARCHFLOW_DB = "D:\MyResearch\researchflow.db"
$env:RESEARCHFLOW_LOGS = "D:\MyResearch\logs"
$env:RESEARCHFLOW_SEED = "false"
$env:RESEARCHFLOW_AI_ENABLED = "false"
.\mvnw.cmd javafx:run
```

Startup checks paths, directory writability, timeout values, flags, endpoint format, and model identifier format. AI endpoints must use HTTP(S), with no embedded credentials, query string, or fragment. Model availability is checked through the runtime, not guaranteed by configuration validation.

| Problem / message | What to check or do |
|---|---|
| No studies after restart | Check that you launched with the same database path and working directory; enable Show archived if needed |
| Archived studies are read-only | View existing records; this study cannot accept writes, including report export |
| Cannot edit a question | Select a question in a Draft form; Active and Closed forms lock structure |
| Cannot collect a response | Select an Active form in a writable study |
| Submission validation failed | Complete required answers, use the expected types, and respect numeric bounds |
| Import stopped with partial results | Save its receipt and inspect imported/failed/unprocessed counts before retrying |
| No responses match the current query | Clear filters/search and check whether a version restore changed live membership |
| Analysis does not reflect a correction | Create a new snapshot and select it for a new analysis |
| Group comparison rejected | Use a Number outcome and supported categorical grouping variable, exactly two groups, and at least two usable rows per group |
| Approve is disabled after editing text | Save wording, reopen the finding, and approve the saved version |
| Finding missing from report | Check that it is Approved; changed approved wording becomes Draft |
| Historical evidence cannot be reconstructed | Read the explicit notice/raw details; do not treat missing fields as valid results |
| AI unavailable | Check the enabled flag, running runtime, configured endpoint, and installed models; manual analysis still works |
| AI request times out | Inspect runtime availability/model load; increase the configured timeout within its valid range if appropriate |
| File saved; audit incomplete | Retain the existing HTML and its hash; the audit write failed, not the file publication |
| Recovery rejected | Select a valid supported ResearchFlow database backup; follow the recovery guide |
| Startup path/configuration error | Correct the named setting and use separate writable database/log paths |
| `release version 25 not supported` | Use JDK 25 or newer and check `JAVA_HOME` |
| Maven certificate/download error | Follow the trust-store instructions in the recovery/release guide; do not disable certificate validation |

## 19. Limits and everyday checklist

The current application has five analysis methods, local desktop collection, dataset import (CSV/TSV/JSON/Excel), and HTML report export. It does not provide regression/ANOVA, public survey hosting, cloud synchronization, authentication, mobile collection, PDF/DOCX export, a general undo system, or a finding-revision-history screen. Import preview is memory-based, and import/bulk review can finish partially. Some long-running work stops only at a safe boundary after cancellation.

For routine work:

1. Open the intended study and verify the database location when changing machines or launch methods.
2. Design and preview forms before activation.
3. Inspect collection/import results; retain partial-import receipts.
4. Correct documented errors and review quality issues.
5. Save a dataset snapshot before the analyses you plan to report.
6. Check each analysis's version, sample size, filters, and warnings.
7. Save and approve accurate finding wording.
8. Preview/export the report and read its file/audit outcome.
9. Create a database backup and retain an independent copy.
10. Archive only when you intend to stop making changes to the study.

Related documents: [setup](../README.md), [backup/recovery and release procedures](RECOVERY_AND_RELEASE.md), [P1 integrity report](INTEGRITY_IMPLEMENTATION_REPORT.md), and [P2 reliability report](P2_RELIABILITY_IMPLEMENTATION_REPORT.md).
