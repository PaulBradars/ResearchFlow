# Phase 1 database model

The initial migration creates the whole lifecycle skeleton so later phases add behavior without
renaming core identifiers or rebuilding foreign-key relationships.

```mermaid
erDiagram
    STUDIES ||--o{ RESEARCH_QUESTIONS : defines
    STUDIES ||--o{ FORMS : owns
    FORMS ||--o{ FORM_SECTIONS : contains
    FORMS ||--o{ QUESTIONS : contains
    QUESTIONS ||--o{ QUESTION_OPTIONS : offers
    FORMS ||--o{ RESPONSES : collects
    RESPONSES ||--o{ ANSWERS : contains
    QUESTIONS ||--o{ ANSWERS : receives
    STUDIES ||--o{ DATASET_VERSIONS : snapshots
    DATASET_VERSIONS ||--o{ VERSION_ANSWER_SNAPSHOTS : contains
    STUDIES ||--o{ QUALITY_ISSUES : flags
    DATASET_VERSIONS ||--o{ ANALYSES : binds
    ANALYSES ||--o{ FINDINGS : supports
    STUDIES ||--o{ AUDIT_LOGS : records
    STUDIES ||--o{ CHAT_REFERENCES : scopes
```

IDs are application-generated UUID strings. Times are ISO-8601 UTC strings; dates are ISO local
dates. Status columns use database `CHECK` constraints as a second line of defense after domain
validation.
