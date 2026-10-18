CREATE TABLE IF NOT EXISTS integration_admin_command (
    command_id VARCHAR(64) PRIMARY KEY,
    operation VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    response_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT chk_integration_admin_command_state CHECK (state IN ('STARTED', 'COMPLETED', 'OUTCOME_UNKNOWN')),
    CONSTRAINT chk_integration_admin_command_response CHECK (state <> 'COMPLETED' OR response_json IS NOT NULL)
);
