CREATE TABLE runtime_target_retirement (
    singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
    deployment_id VARCHAR(128),
    target_profile_id VARCHAR(128),
    release_id VARCHAR(128),
    tenant_id VARCHAR(128),
    topology_fingerprint VARCHAR(64),
    state VARCHAR(32) NOT NULL,
    fence_epoch BIGINT NOT NULL CHECK (fence_epoch >= 0),
    operation_id VARCHAR(160),
    request_fingerprint VARCHAR(128),
    drain_deadline TIMESTAMP WITH TIME ZONE,
    authority_revoked BOOLEAN NOT NULL,
    reclaim_started BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

INSERT INTO runtime_target_retirement
    (singleton_id, state, fence_epoch, authority_revoked, reclaim_started, updated_at)
VALUES (1, 'ACTIVE', 0, FALSE, FALSE, CURRENT_TIMESTAMP);

CREATE TABLE runtime_target_retirement_command (
    fence_epoch BIGINT PRIMARY KEY,
    operation_id VARCHAR(160) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE runtime_target_retirement_snapshot (
    snapshot_id VARCHAR(64) PRIMARY KEY,
    fence_epoch BIGINT NOT NULL,
    operation_id VARCHAR(160) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    snapshot_json TEXT NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE runtime_target_retirement_turn (
    permit_id VARCHAR(64) PRIMARY KEY,
    fence_epoch BIGINT NOT NULL,
    conversation_id VARCHAR(160) NOT NULL,
    owner_id VARCHAR(256) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_runtime_retirement_turn_epoch ON runtime_target_retirement_turn (fence_epoch);
