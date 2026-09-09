ALTER TABLE analyses ADD COLUMN evidence_schema_version INTEGER NOT NULL DEFAULT 0;
ALTER TABLE analyses ADD COLUMN variables_json TEXT;
