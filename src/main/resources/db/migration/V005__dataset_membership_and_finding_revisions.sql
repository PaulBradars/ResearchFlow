ALTER TABLE responses ADD COLUMN in_dataset INTEGER NOT NULL DEFAULT 1 CHECK (in_dataset IN (0, 1));
ALTER TABLE answers ADD COLUMN is_present INTEGER NOT NULL DEFAULT 1 CHECK (is_present IN (0, 1));
ALTER TABLE dataset_versions ADD COLUMN membership_complete INTEGER NOT NULL DEFAULT 0 CHECK (membership_complete IN (0, 1));

CREATE TABLE version_response_snapshots (
    dataset_version_id TEXT NOT NULL REFERENCES dataset_versions(id) ON DELETE CASCADE,
    response_id TEXT NOT NULL REFERENCES responses(id) ON DELETE RESTRICT,
    excluded INTEGER NOT NULL CHECK (excluded IN (0, 1)),
    PRIMARY KEY (dataset_version_id, response_id)
);

INSERT INTO version_response_snapshots(dataset_version_id, response_id, excluded)
SELECT dataset_version_id, response_id, MAX(excluded)
FROM version_answer_snapshots GROUP BY dataset_version_id, response_id;

CREATE TABLE finding_text_revisions (
    id TEXT PRIMARY KEY,
    finding_id TEXT NOT NULL REFERENCES findings(id) ON DELETE RESTRICT,
    previous_text TEXT NOT NULL,
    previous_status TEXT NOT NULL,
    previous_approved_at TEXT,
    changed_at TEXT NOT NULL
);

CREATE INDEX idx_version_responses_response ON version_response_snapshots(response_id);
CREATE INDEX idx_finding_revisions_finding ON finding_text_revisions(finding_id, changed_at);
