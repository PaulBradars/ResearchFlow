CREATE TABLE studies (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 160),
    description TEXT NOT NULL DEFAULT '',
    objectives TEXT NOT NULL DEFAULT '',
    researcher TEXT NOT NULL DEFAULT '',
    start_date TEXT,
    end_date TEXT,
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    CHECK (end_date IS NULL OR start_date IS NULL OR end_date >= start_date)
);

CREATE TABLE research_questions (
    id TEXT PRIMARY KEY,
    study_id TEXT NOT NULL REFERENCES studies(id) ON DELETE CASCADE,
    question_text TEXT NOT NULL CHECK (length(trim(question_text)) > 0),
    position INTEGER NOT NULL CHECK (position >= 0),
    created_at TEXT NOT NULL,
    UNIQUE (study_id, position)
);

CREATE TABLE forms (
    id TEXT PRIMARY KEY,
    study_id TEXT NOT NULL REFERENCES studies(id) ON DELETE RESTRICT,
    title TEXT NOT NULL,
    description TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED')),
    version INTEGER NOT NULL DEFAULT 1 CHECK (version > 0),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

CREATE TABLE form_sections (
    id TEXT PRIMARY KEY,
    form_id TEXT NOT NULL REFERENCES forms(id) ON DELETE CASCADE,
    title TEXT NOT NULL DEFAULT '',
    description TEXT NOT NULL DEFAULT '',
    position INTEGER NOT NULL CHECK (position >= 0),
    UNIQUE (form_id, position)
);

CREATE TABLE questions (
    id TEXT PRIMARY KEY,
    form_id TEXT NOT NULL REFERENCES forms(id) ON DELETE CASCADE,
    section_id TEXT REFERENCES form_sections(id) ON DELETE SET NULL,
    variable_key TEXT NOT NULL,
    label TEXT NOT NULL,
    help_text TEXT NOT NULL DEFAULT '',
    question_type TEXT NOT NULL CHECK (question_type IN
        ('SHORT_TEXT', 'NUMBER', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'YES_NO', 'LIKERT', 'RATING', 'DATE')),
    required INTEGER NOT NULL DEFAULT 0 CHECK (required IN (0, 1)),
    position INTEGER NOT NULL CHECK (position >= 0),
    configuration_json TEXT NOT NULL DEFAULT '{}',
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE (form_id, variable_key),
    UNIQUE (form_id, position)
);

CREATE TABLE question_options (
    id TEXT PRIMARY KEY,
    question_id TEXT NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    option_value TEXT NOT NULL,
    label TEXT NOT NULL,
    position INTEGER NOT NULL CHECK (position >= 0),
    UNIQUE (question_id, option_value),
    UNIQUE (question_id, position)
);

CREATE TABLE responses (
    id TEXT PRIMARY KEY,
    form_id TEXT NOT NULL REFERENCES forms(id) ON DELETE RESTRICT,
    form_version INTEGER NOT NULL CHECK (form_version > 0),
    status TEXT NOT NULL CHECK (status IN ('COMPLETE', 'EXCLUDED')),
    started_at TEXT,
    submitted_at TEXT NOT NULL,
    duration_seconds INTEGER CHECK (duration_seconds IS NULL OR duration_seconds >= 0)
);

CREATE TABLE answers (
    id TEXT PRIMARY KEY,
    response_id TEXT NOT NULL REFERENCES responses(id) ON DELETE CASCADE,
    question_id TEXT NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    value_text TEXT,
    value_number REAL,
    value_boolean INTEGER CHECK (value_boolean IS NULL OR value_boolean IN (0, 1)),
    value_date TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    UNIQUE (response_id, question_id),
    CHECK ((value_text IS NOT NULL) + (value_number IS NOT NULL) +
           (value_boolean IS NOT NULL) + (value_date IS NOT NULL) <= 1)
);

CREATE TABLE dataset_versions (
    id TEXT PRIMARY KEY,
    study_id TEXT NOT NULL REFERENCES studies(id) ON DELETE RESTRICT,
    parent_version_id TEXT REFERENCES dataset_versions(id) ON DELETE RESTRICT,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    reason TEXT NOT NULL,
    change_summary TEXT NOT NULL DEFAULT '',
    created_at TEXT NOT NULL,
    is_active INTEGER NOT NULL DEFAULT 0 CHECK (is_active IN (0, 1)),
    UNIQUE (study_id, version_number)
);

CREATE TABLE version_answer_snapshots (
    dataset_version_id TEXT NOT NULL REFERENCES dataset_versions(id) ON DELETE CASCADE,
    answer_id TEXT NOT NULL,
    response_id TEXT NOT NULL,
    question_id TEXT NOT NULL,
    value_json TEXT NOT NULL,
    excluded INTEGER NOT NULL DEFAULT 0 CHECK (excluded IN (0, 1)),
    PRIMARY KEY (dataset_version_id, answer_id)
);

CREATE TABLE quality_issues (
    id TEXT PRIMARY KEY,
    study_id TEXT NOT NULL REFERENCES studies(id) ON DELETE RESTRICT,
    dataset_version_id TEXT REFERENCES dataset_versions(id) ON DELETE RESTRICT,
    issue_type TEXT NOT NULL,
    severity TEXT NOT NULL CHECK (severity IN ('INFO', 'WARNING', 'ERROR')),
    status TEXT NOT NULL CHECK (status IN ('OPEN', 'RESOLVED', 'ACCEPTED', 'DEFERRED')),
    response_id TEXT,
    question_id TEXT,
    explanation TEXT NOT NULL,
    resolution_note TEXT,
    created_at TEXT NOT NULL,
    resolved_at TEXT
);

CREATE TABLE analyses (
    id TEXT PRIMARY KEY,
    study_id TEXT NOT NULL REFERENCES studies(id) ON DELETE RESTRICT,
    dataset_version_id TEXT NOT NULL REFERENCES dataset_versions(id) ON DELETE RESTRICT,
    method TEXT NOT NULL,
    plan_json TEXT NOT NULL,
    result_json TEXT NOT NULL,
    sample_size INTEGER NOT NULL CHECK (sample_size >= 0),
    warnings_json TEXT NOT NULL DEFAULT '[]',
    source TEXT NOT NULL CHECK (source IN ('MANUAL', 'AI')),
    model_metadata_json TEXT,
    created_at TEXT NOT NULL
);

CREATE TABLE findings (
    id TEXT PRIMARY KEY,
    study_id TEXT NOT NULL REFERENCES studies(id) ON DELETE RESTRICT,
    analysis_id TEXT NOT NULL REFERENCES analyses(id) ON DELETE RESTRICT,
    dataset_version_id TEXT NOT NULL REFERENCES dataset_versions(id) ON DELETE RESTRICT,
    text TEXT NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('DRAFT', 'APPROVED', 'REJECTED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    approved_at TEXT
);

CREATE TABLE audit_logs (
    id TEXT PRIMARY KEY,
    study_id TEXT REFERENCES studies(id) ON DELETE RESTRICT,
    event_type TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id TEXT,
    actor TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    details_json TEXT NOT NULL DEFAULT '{}'
);

CREATE TABLE chat_references (
    id TEXT PRIMARY KEY,
    study_id TEXT NOT NULL REFERENCES studies(id) ON DELETE RESTRICT,
    analysis_id TEXT REFERENCES analyses(id) ON DELETE SET NULL,
    role TEXT NOT NULL CHECK (role IN ('USER', 'ASSISTANT', 'SYSTEM')),
    content TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE INDEX idx_research_questions_study ON research_questions(study_id, position);
CREATE INDEX idx_forms_study_status ON forms(study_id, status);
CREATE INDEX idx_questions_form_position ON questions(form_id, position);
CREATE INDEX idx_responses_form_submitted ON responses(form_id, submitted_at);
CREATE INDEX idx_answers_response ON answers(response_id);
CREATE INDEX idx_answers_question ON answers(question_id);
CREATE INDEX idx_versions_study_number ON dataset_versions(study_id, version_number);
CREATE INDEX idx_issues_study_status ON quality_issues(study_id, status);
CREATE INDEX idx_analyses_study_created ON analyses(study_id, created_at);
CREATE INDEX idx_findings_study_status ON findings(study_id, status);
CREATE INDEX idx_audit_study_occurred ON audit_logs(study_id, occurred_at);
CREATE INDEX idx_chat_study_created ON chat_references(study_id, created_at);
