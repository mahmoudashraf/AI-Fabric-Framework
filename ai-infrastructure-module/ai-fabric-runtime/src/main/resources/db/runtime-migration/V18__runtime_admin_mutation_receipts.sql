CREATE TABLE IF NOT EXISTS loomai_runtime_admin_command (
    command_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    customer_id VARCHAR(128) NOT NULL,
    deployment_id VARCHAR(128) NOT NULL,
    target_profile_id VARCHAR(128) NOT NULL,
    release_id VARCHAR(128) NOT NULL,
    operation VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    response_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_runtime_admin_command_state CHECK (state IN ('STARTED', 'COMPLETED', 'OUTCOME_UNKNOWN')),
    CONSTRAINT chk_runtime_admin_command_response CHECK (state <> 'COMPLETED' OR response_json IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_runtime_admin_command_scope
    ON loomai_runtime_admin_command (deployment_id, target_profile_id, release_id, updated_at);
