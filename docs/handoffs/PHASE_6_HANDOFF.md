# Phase 6 handoff: Local AI and "Ask Your Data"

Date: 2026-09-05  
Phase status: implementation complete; clean build and test suite passing  
Architecture reference: `../architecture/CURRENT_IMPLEMENTED_ARCHITECTURE.md`

## Delivered outcome

- `LlmClient` adapter contract with a health check, timeout, and clear error mapping
  (`LlmException.Kind`: `UNAVAILABLE`, `TIMEOUT`, `MALFORMED_RESPONSE`); `LocalLlmClient` targets a
  local Ollama-compatible `/api/generate` endpoint over plain `java.net.http.HttpClient` (no new
  Maven dependency); `DisabledLlmClient` is a no-op used when AI is turned off in configuration.
- `AiPromptBuilder` builds a versioned plan-generation system prompt (every variable's ID/label/type/
  options, the five methods' variable rules, and the exact required JSON shape) and a versioned
  explanation prompt containing **only** the already-computed `EvidenceBundle`.
- `AnalysisPlanParser` defensively parses the model's raw text into an `AnalysisPlan` — rejecting an
  unsupported method name, a missing/malformed/out-of-Study variable ID, or an unsupported filter
  operator — without ever guessing or silently defaulting a field.
- `AnalysisFacade` ("Ask Your Data"): interpret -> parse -> **the same `AnalysisPlanValidator` and
  `AnalysisService.run` manual analysis uses** -> store evidence -> explain -> store the
  conversation. The LLM never computes a statistic and never bypasses validation — a structurally
  valid but semantically unsupported plan (e.g. correlating a non-number variable) is rejected by
  `AnalysisPlanValidator` exactly as it would be from the manual UI.
- AI-sourced analyses are persisted with `source='AI'` and a `model_metadata_json` (model, runtime,
  prompt-template version); the conversation itself (question + explanation) is persisted in
  `chat_references`, linked to the analysis via `analysis_id` — a follow-up can be resolved through
  that id rather than resending the whole dataset.
- A new "Ask Your Data" tab in the Analysis workspace: a single-question chat-style panel, disabled
  with a clear notice when AI is unavailable, that always shows the full evidence alongside the
  explanation.
- No new migration: `chat_references` and `analyses.model_metadata_json` already existed from V001.

## User walkthrough

1. Open a Study and select **Analysis** → **Ask Your Data**.
2. If local AI is unavailable, a notice explains that manual analysis (the **Run analysis** tab)
   still works, and the question field is disabled.
3. Otherwise, type a question in plain language (e.g. *"How is sleep duration associated with
   academic focus?"*) and select **Ask**.
4. The transcript shows your question, the AI's plain-language explanation, and the full evidence
   panel (method, variables, sample size, warnings, and the typed result) — identical to what a
   manual analysis of the same plan would show.
5. Reopening the tab reloads the full conversation history for the Study, including analyses run in
   earlier sessions.

## Defense in depth: how a bad AI response is stopped before execution

| Failure | Where it is caught | What happens |
|---|---|---|
| Runtime unreachable / disabled | `LlmClient.isAvailable()` | `ask()` throws `LlmException(UNAVAILABLE)` before any prompt is sent |
| Timeout mid-request | `LocalLlmClient.complete` | `LlmException(TIMEOUT)`; nothing is persisted |
| No JSON object in the response | `AnalysisPlanParser` | `LlmException(MALFORMED_RESPONSE)` |
| Unknown method / bad or out-of-Study variable ID / bad filter operator | `AnalysisPlanParser` | `LlmException(MALFORMED_RESPONSE)` |
| Well-formed JSON, wrong variable type for the method, duplicate variables, invalid filter value | `AnalysisPlanValidator` (inside `AnalysisService.run`) — **the exact validator manual analysis uses** | `ValidationException`; nothing is executed or persisted |
| Explanation call fails after a successful, already-persisted analysis | `AnalysisFacade.explain` | A graceful fallback explanation is returned; the evidence and its `chat_references` entries are unaffected |

## Test inventory added

| Test | Protects |
|---|---|
| `AnalysisPlanParserTest` | Every defensive-parsing rejection path, plus tolerance for markdown fences/prose around the JSON |
| `AiPromptBuilderTest` | The plan prompt lists every variable/method; the explanation prompt contains only the stored evidence |
| `AnalysisFacadeTest` | **Five representative questions map to valid plans across all five methods**; malformed/unsupported/hallucinated plans never execute; manual analysis works with AI unavailable; an explanation failure never loses computed evidence — using `FakeLlmClient`, never a real network call |

Verification baseline:

```text
Tests run: 60, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Configuration

New `AppConfig` settings (system property / environment variable, all optional with sensible
defaults): `researchflow.ai.enabled` / `RESEARCHFLOW_AI_ENABLED` (default `true`),
`researchflow.ai.baseUrl` / `RESEARCHFLOW_AI_BASE_URL` (default `http://localhost:11434`),
`researchflow.ai.model` / `RESEARCHFLOW_AI_MODEL` (default `llama3.2`),
`researchflow.ai.timeoutSeconds` / `RESEARCHFLOW_AI_TIMEOUT_SECONDS` (default `20`). Setting
`aiEnabled=false` (or simply not running a local Ollama-compatible server) exercises the same
"AI unavailable" path the tests cover — the rest of the application is unaffected either way.

Cancellation is provided by ordinary Java thread interruption: `ResearchFlowApplication.stop()`
already calls `executor.shutdownNow()`, which interrupts any in-flight blocked HTTP call running on
the background executor. There is no separate mid-flight "Cancel" button in this phase.

## Guardrails

- The LLM interprets (plan generation) and explains; it never computes a statistic, and every plan
  it produces passes through the identical `AnalysisPlanValidator`/`AnalysisService.run` path as a
  manually built plan.
- Never execute AI-generated SQL — the AI only ever supplies variable IDs, a method name, and filter
  values, all resolved against authoritative `Question` metadata, never a raw column/table reference.
- AI-generated analyses remain traceable: `source='AI'`, `model_metadata_json`, and the
  question/explanation pair in `chat_references`, all linked by `analysis_id`.
- Explanations are constrained to the stored `EvidenceBundle`; they cannot alter it.
- Keep database and network work off the JavaFX thread (unchanged — routed through `Async`).
- Never edit an already-applied migration.
- Standard tests never call a real LLM; `FakeLlmClient` stands in everywhere.

## Phase 7 starting point

1. Implement chart selection/builders over structured `AnalysisResult` data (bar, histogram,
   scatter), never raw AI prose.
2. Generate a draft `Finding` from selected evidence (manual or AI); allow edit, approve, reject —
   unapproved findings stay out of the eventual report by default.
3. Preserve analysis/version links when finding wording changes.
4. Build report composition from stored Study data, quality summary, evidence, charts, and approved
   findings, with an HTML export and an audit-logged generation event.
