# ResearchFlow AI

A local-first desktop application for the full research data lifecycle: design a form, collect
responses, inspect and clean the dataset, snapshot dataset versions, run deterministic analyses
(manually or by asking a question in plain language via a local AI model), turn evidence into
approved findings, and export a report — all offline, with SQLite as the only datastore.

This guide gets the project running on a fresh machine.

## Prerequisites

| Requirement | Version | Notes |
|---|---|---|
| JDK | **25 or newer** | Matches `pom.xml`'s `<maven.compiler.release>`. Verified against Eclipse Temurin/OpenJDK 26. |
| Apache Maven | 3.9+ | No wrapper is checked into the repo — install Maven separately. |
| Ollama (optional) | any recent version | Only needed for the **Ask Your Data** feature. Everything else works fully without it. |

Nothing else to install: SQLite is an embedded JDBC driver (no server), and JavaFX is pulled in
automatically by Maven the first time you build.

## 1. Get the code

```powershell
git clone https://github.com/MPlab007/ResearchFlow.git
cd ResearchFlow
```

## 2. Check your JDK

```powershell
java -version
```

This must report **25 or higher**. If your default `java` is older, install a newer JDK (e.g. from
[adoptium.net](https://adoptium.net/)) and point `JAVA_HOME` at it for your shell before building:

```powershell
$env:JAVA_HOME = "C:\Path\To\your\jdk-25-or-newer"
```

## 3. Build and test

```powershell
mvn clean test
```

This compiles everything and runs the full test suite. It should finish with `BUILD SUCCESS` and
zero failures/errors. The standard test suite never calls a real LLM, so this step works identically
whether or not Ollama is installed.

## 4. Run the application

```powershell
mvn javafx:run
```

On first launch the app automatically:

- creates `data/researchflow.db` (a local SQLite file) and applies its migrations,
- writes structured JSON-line logs to `logs/`,
- seeds one small demo Study so there's something to look at immediately.

The window opens directly into the Studies list. From there, a Study's left-hand navigation gives
you every workspace: Form, Responses, Import, Dataset, Quality / Versions, Analysis, Findings /
Report.

## 5. (Optional) enable Ask Your Data — local AI

The app works completely without this step; only the **Ask Your Data** tab needs it.

1. Install [Ollama](https://ollama.com/download) and make sure it's running (it starts as a
   background service after install; check with `curl http://localhost:11434/api/tags`).
2. Pull at least one model:
   ```powershell
   ollama pull llama3.2
   ```
   (Any model works — see the configuration table below to point the app at a different one.)
3. Launch the app as in step 4. Open a Study → **Analysis** → **Ask Your Data**; it detects
   availability automatically and the question field enables itself.

If Ollama isn't installed or isn't reachable, Ask Your Data shows a clear "not available" notice
and every other workspace is unaffected.

## 6. Configuration (all optional — sensible defaults apply)

Every setting can be provided as an environment variable, or as a Java system property with
`-Dresearchflow.xxx=value` (system properties take precedence).

| Setting | Environment variable | Default | Purpose |
|---|---|---|---|
| Database file | `RESEARCHFLOW_DB` | `data/researchflow.db` | Where the SQLite file lives |
| Log directory | `RESEARCHFLOW_LOGS` | `logs` | Where JSON-line logs are written |
| Seed demo data | `RESEARCHFLOW_SEED` | `true` | Populate one small demo Study on first run |
| AI enabled | `RESEARCHFLOW_AI_ENABLED` | `true` | Turn Ask Your Data off entirely |
| AI base URL | `RESEARCHFLOW_AI_BASE_URL` | `http://localhost:11434` | Ollama (or compatible) endpoint |
| AI model | `RESEARCHFLOW_AI_MODEL` | *(blank — auto-detect)* | Left blank, the app uses whichever model Ollama actually has installed; set it only to pick a specific one when several are pulled |
| AI timeout (seconds) | `RESEARCHFLOW_AI_TIMEOUT_SECONDS` | `60` | Local models can take 15–30s+ per call, more on a cold start |

You normally don't need to set `RESEARCHFLOW_AI_MODEL` at all — whatever you `ollama pull` is used
automatically. Set it only if you have more than one model installed and want a specific one:

```powershell
$env:RESEARCHFLOW_AI_MODEL = "qwen3:8b"
mvn javafx:run
```

> PowerShell environment variables only last for that terminal session. Set them again in any new
> terminal, or add them permanently under *System Properties → Environment Variables* if you want
> them to stick without re-typing.

## 7. Resetting to a clean state

`data/` and `logs/` are git-ignored, generated folders — delete them and relaunch to start over:

```powershell
Remove-Item -Recurse -Force data, logs
mvn javafx:run
```

## 8. Troubleshooting

- **`release version 25 not supported`** — your active JDK is older than 25. Install a newer one
  and point `JAVA_HOME` at it (step 2).
- **Ask Your Data says AI unavailable** — Ollama isn't running, has no model pulled at all, or isn't
  reachable at the configured base URL. Check with `curl http://localhost:11434/api/tags`.
- **Ask Your Data fails immediately naming a model** — you explicitly set `RESEARCHFLOW_AI_MODEL` to
  something not installed; the error message lists what actually is. Run `ollama list` and either
  clear the variable (auto-detect) or set it to one of the listed names.
- **Ask Your Data seems to hang** — local models can genuinely take 15–30+ seconds per request,
  especially "thinking" models and on a cold start where the model must first load into memory.
  That's expected, not a freeze; raise `RESEARCHFLOW_AI_TIMEOUT_SECONDS` if you need more headroom.
- **A large imported dataset feels slow** — make sure you're on the latest code; write-heavy
  database operations use WAL mode and batched inserts, and have been verified against a
  10,000-response dataset.

## Documentation

- [`docs/README.md`](docs/README.md) — architecture reference, phase-by-phase handoffs, and diagrams.
- [`docs/planning/DEVELOPMENT_ROADMAP.md`](docs/planning/DEVELOPMENT_ROADMAP.md) — the delivery plan
  this project follows.
