CREATE TABLE answer_corrections (
    id TEXT PRIMARY KEY,
    study_id TEXT NOT NULL REFERENCES studies(id) ON DELETE RESTRICT,
    response_id TEXT NOT NULL REFERENCES responses(id) ON DELETE RESTRICT,
    question_id TEXT NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    answer_id TEXT NOT NULL REFERENCES answers(id) ON DELETE RESTRICT,
    old_value_json TEXT NOT NULL,
    new_value_json TEXT NOT NULL,
    reason TEXT NOT NULL CHECK (length(trim(reason)) BETWEEN 3 AND 1000),
    actor TEXT NOT NULL,
    corrected_at TEXT NOT NULL
);

CREATE INDEX idx_answer_corrections_response ON answer_corrections(response_id, corrected_at);
CREATE INDEX idx_answer_corrections_question ON answer_corrections(question_id, corrected_at);
CREATE INDEX idx_responses_status_submitted ON responses(status, submitted_at);
CREATE INDEX idx_audit_entity_occurred ON audit_logs(entity_type, entity_id, occurred_at);
