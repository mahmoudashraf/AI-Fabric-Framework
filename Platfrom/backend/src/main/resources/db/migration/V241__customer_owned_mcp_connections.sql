CREATE TABLE platform_mcp_connections (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL REFERENCES platform_deployments(id) ON DELETE CASCADE,
    customer_id VARCHAR(64),
    target_profile_id VARCHAR(128) NOT NULL REFERENCES deployment_target_profiles(id),
    target_bindings_json TEXT NOT NULL DEFAULT '[]',
    server_ref VARCHAR(128) NOT NULL,
    name VARCHAR(160) NOT NULL,
    endpoint_url TEXT NOT NULL,
    connection_model VARCHAR(32) NOT NULL CHECK (connection_model IN ('PUBLIC', 'BUSINESS')),
    transport VARCHAR(32) NOT NULL,
    auth_config_json TEXT NOT NULL,
    revision BIGINT NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    auth_status VARCHAR(64) NOT NULL,
    oauth_metadata_json TEXT,
    encrypted_tokens TEXT,
    token_expires_at TIMESTAMP WITH TIME ZONE,
    last_discovery_id VARCHAR(64),
    last_verification_json TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (deployment_id, server_ref),
    CHECK (connection_model <> 'PUBLIC' OR (encrypted_tokens IS NULL AND token_expires_at IS NULL))
);
CREATE INDEX idx_mcp_connections_deployment ON platform_mcp_connections(deployment_id, target_profile_id);
CREATE TABLE platform_mcp_discovery_snapshots (
    id VARCHAR(64) PRIMARY KEY,
    connection_id VARCHAR(64) NOT NULL REFERENCES platform_mcp_connections(id) ON DELETE CASCADE,
    connection_revision BIGINT NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    snapshot_json TEXT NOT NULL,
    content_hash VARCHAR(64) NOT NULL
);
CREATE TABLE platform_mcp_import_receipts (
    id VARCHAR(64) PRIMARY KEY,
    connection_id VARCHAR(64) NOT NULL REFERENCES platform_mcp_connections(id) ON DELETE CASCADE,
    snapshot_id VARCHAR(64) NOT NULL REFERENCES platform_mcp_discovery_snapshots(id) ON DELETE CASCADE,
    draft_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    receipt_json TEXT NOT NULL,
    before_actions_json TEXT NOT NULL,
    after_actions_hash VARCHAR(64) NOT NULL,
    undone_at TIMESTAMP WITH TIME ZONE,
    undo_draft_revision INTEGER
);
CREATE TABLE platform_mcp_oauth_attempts (
    state_hash VARCHAR(64) PRIMARY KEY,
    connection_id VARCHAR(64) NOT NULL REFERENCES platform_mcp_connections(id) ON DELETE CASCADE,
    connection_revision BIGINT NOT NULL,
    actor_id VARCHAR(128) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    browser_binding_hash VARCHAR(64) NOT NULL,
    return_path TEXT NOT NULL,
    encrypted_verifier TEXT,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_mcp_oauth_attempt_expiry ON platform_mcp_oauth_attempts(expires_at);
