# Phase 4 quality review and versioning flow

```mermaid
flowchart TB
    UI[QualityIssuesView] -->|scan| QS[QualityService]
    QS --> FR[FormRepository]
    QS --> RR[ResponseRepository.findFullByStudy]
    QS --> CTX[QualityScanContext]
    CTX --> H1[MissingRequiredHandler]
    H1 --> H2[InvalidRangeHandler]
    H2 --> H3[DuplicateResponseHandler]
    H3 --> H4[OutlierHandler]
    H4 --> H5[FastSubmissionHandler]
    H5 -->|detected issues| QR[QualityRepository.reconcile]
    QR --> DB[(quality_issues)]

    UI -->|ReviewCommand| RS[QualityReviewService]
    RS -->|Correct| DCS[DatasetCorrectionService]
    RS -->|Exclude / Accept / Defer| QR
    DCS --> DR[DatasetRepository]
    DR --> TM1[TransactionManager]
    TM1 --> ANS[answers + answer_corrections + audit_logs]

    UI2[DatasetVersionsView] -->|createSnapshot / restore| VS[VersionService]
    VS --> VR[VersionRepository]
    VR --> TM2[TransactionManager]
    TM2 --> VER[dataset_versions]
    TM2 --> SNAP[version_answer_snapshots]
    TM2 --> AUD[audit_logs]
```

Detection never writes to the database; only `QualityRepository.reconcile` (new issues) and a
`ReviewCommand` (status changes) do. `VersionRepository.restore` writes live `answers`/`responses`
to match an older snapshot, then calls the same snapshot routine used by `createSnapshot` to record
the result as a new version — it never edits an existing `dataset_versions` row.
