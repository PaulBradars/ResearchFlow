# Phase 5 analysis and evidence flow

```mermaid
flowchart TB
    UI[AnalysisRunView] -->|AnalysisPlan| AS[AnalysisService]
    AS --> FR[FormRepository]
    AS -->|validate| PV[AnalysisPlanValidator]
    AS -->|resolve version| VR[VersionRepository]
    VR -->|no active version| CS[createSnapshot: baseline]
    AS --> AR[AnalysisRepository.loadVersionData]
    AR --> SNAP[(version_answer_snapshots)]
    AS --> AD[AnalysisData: group + filter]
    AD --> CTX[AnalysisContext]
    CTX --> STR[AnalysisStrategy]
    STR --> RES[AnalysisResult]
    RES --> W[AnalysisWarnings]
    W --> EV[EvidenceBundle]
    EV --> UI
    AS -->|save| AR2[AnalysisRepository.save]
    AR2 --> DB[(analyses + audit_logs)]
    UI2[AnalysisHistoryView] --> AR3[AnalysisRepository.findByStudy / findById]
    AR3 --> DB
```

Analysis never reads the live `answers` table. `AnalysisRepository.loadVersionData` reconstructs
typed `Answer`s from one dataset version's immutable snapshot, so a stored plan re-run against the
same version reproduces its original result even if the live dataset changes afterward. Validation
(`AnalysisPlanValidator`) runs before any strategy executes; an invalid plan never reaches
`AnalysisContext`.
