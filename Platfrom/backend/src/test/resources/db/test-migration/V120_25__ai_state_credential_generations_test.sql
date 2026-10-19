-- H2 mirror. The production migration is independently exercised on PostgreSQL.
CREATE TABLE deployment_ai_state_storage_credential_generations (
    binding_id VARCHAR(64) NOT NULL,
    version_id VARCHAR(64) NOT NULL,
    generation_id VARCHAR(64) NOT NULL UNIQUE,
    generation_json TEXT NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (binding_id, version_id),
    FOREIGN KEY (binding_id, version_id) REFERENCES deployment_ai_state_storage_approvals(binding_id, version_id) ON DELETE CASCADE
);
