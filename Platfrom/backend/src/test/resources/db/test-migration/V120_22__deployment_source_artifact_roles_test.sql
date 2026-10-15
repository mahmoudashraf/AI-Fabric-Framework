ALTER TABLE deployment_source_artifacts
    ADD COLUMN artifact_role VARCHAR(64) NOT NULL DEFAULT 'RUNTIME';

CREATE INDEX idx_deployment_source_artifacts_role_service
    ON deployment_source_artifacts (artifact_role, service_name, created_at DESC);
