# Phase 6 "Ask Your Data" flow

```mermaid
flowchart TB
    UI[AskYourDataView] -->|question| FA[AnalysisFacade.ask]
    FA -->|isAvailable?| LLM[LlmClient]
    LLM -->|false| ERR1[LlmException UNAVAILABLE]
    FA --> PB[AiPromptBuilder.planPrompt]
    PB --> LLM
    LLM -->|raw text| PP[AnalysisPlanParser]
    PP -->|malformed| ERR2[LlmException MALFORMED_RESPONSE]
    PP -->|AnalysisPlan| AS[AnalysisService.run]
    AS -->|schema validation| PV[AnalysisPlanValidator]
    PV -->|unsupported plan| ERR3[ValidationException]
    AS -->|version-bound execution| STR[AnalysisStrategy]
    STR --> EV[EvidenceBundle]
    EV --> DB1[(analyses: source=AI, model_metadata_json)]
    FA --> CU[chat_references: USER]
    FA -->|explanationPrompt: evidence only| LLM
    LLM -->|explanation, or a failure caught locally| FA
    FA --> CA[chat_references: ASSISTANT]
    EV --> UI
```

`AnalysisPlanParser` and `AnalysisPlanValidator` are two independent gates: the parser rejects a
response that isn't even structurally a plan (bad JSON, unknown method, bad variable ID); the
validator — the exact same one manual analysis uses — rejects a structurally valid plan that is
semantically unsupported (wrong variable type, duplicate variables, bad filter). Neither the LLM
nor `AnalysisFacade` can skip either gate. An explanation failure after a successful `AnalysisService.run`
never rolls back or hides the already-persisted evidence — it only changes what text is shown.
