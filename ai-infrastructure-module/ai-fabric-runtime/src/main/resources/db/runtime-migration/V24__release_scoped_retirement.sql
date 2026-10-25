CREATE TABLE runtime_release_retirement_gate (
    tenant_id VARCHAR(128) NOT NULL,
    deployment_id VARCHAR(128) NOT NULL,
    target_profile_id VARCHAR(128) NOT NULL,
    release_id VARCHAR(128) NOT NULL,
    topology_fingerprint VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL,
    fence_epoch BIGINT NOT NULL CHECK (fence_epoch >= 0),
    drain_admission_epoch BIGINT,
    operation_id VARCHAR(160),
    request_fingerprint VARCHAR(128),
    drain_deadline TIMESTAMP WITH TIME ZONE,
    authority_revoked BOOLEAN NOT NULL,
    reclaim_started BOOLEAN NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (tenant_id, deployment_id, target_profile_id, release_id)
);

CREATE TABLE runtime_release_retirement_command (
    tenant_id VARCHAR(128) NOT NULL,
    deployment_id VARCHAR(128) NOT NULL,
    target_profile_id VARCHAR(128) NOT NULL,
    release_id VARCHAR(128) NOT NULL,
    fence_epoch BIGINT NOT NULL,
    operation_id VARCHAR(160) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (tenant_id, deployment_id, target_profile_id, release_id, fence_epoch),
    FOREIGN KEY (tenant_id, deployment_id, target_profile_id, release_id)
        REFERENCES runtime_release_retirement_gate (tenant_id, deployment_id, target_profile_id, release_id)
);

CREATE TABLE runtime_release_retirement_snapshot (
    snapshot_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    deployment_id VARCHAR(128) NOT NULL,
    target_profile_id VARCHAR(128) NOT NULL,
    release_id VARCHAR(128) NOT NULL,
    fence_epoch BIGINT NOT NULL,
    operation_id VARCHAR(160) NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    snapshot_json TEXT NOT NULL,
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (tenant_id, deployment_id, target_profile_id, release_id)
        REFERENCES runtime_release_retirement_gate (tenant_id, deployment_id, target_profile_id, release_id)
);

CREATE TABLE runtime_release_retirement_turn (
    permit_id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(128) NOT NULL,
    deployment_id VARCHAR(128) NOT NULL,
    target_profile_id VARCHAR(128) NOT NULL,
    release_id VARCHAR(128) NOT NULL,
    fence_epoch BIGINT NOT NULL,
    conversation_id VARCHAR(160),
    owner_id VARCHAR(256) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (tenant_id, deployment_id, target_profile_id, release_id)
        REFERENCES runtime_release_retirement_gate (tenant_id, deployment_id, target_profile_id, release_id)
);
CREATE INDEX ix_release_retirement_turn_scope
    ON runtime_release_retirement_turn (tenant_id, deployment_id, target_profile_id, release_id, fence_epoch);

CREATE TABLE runtime_release_index_retirement (
    tenant_id VARCHAR(128) NOT NULL,
    deployment_id VARCHAR(128) NOT NULL,
    target_profile_id VARCHAR(128) NOT NULL,
    release_id VARCHAR(128) NOT NULL,
    operation_id VARCHAR(160) NOT NULL,
    fence_epoch BIGINT NOT NULL,
    request_fingerprint VARCHAR(128) NOT NULL,
    topology_fingerprint VARCHAR(64) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (tenant_id, deployment_id, target_profile_id, release_id, operation_id),
    FOREIGN KEY (tenant_id, deployment_id, target_profile_id, release_id)
        REFERENCES runtime_release_retirement_gate (tenant_id, deployment_id, target_profile_id, release_id)
);

-- A same-release artifact restart must retain a known legacy fence and unsettled attempts.
INSERT INTO runtime_release_retirement_gate
    (tenant_id, deployment_id, target_profile_id, release_id, topology_fingerprint, state, fence_epoch,
     operation_id, request_fingerprint, drain_deadline, authority_revoked, reclaim_started, updated_at)
SELECT tenant_id, deployment_id, target_profile_id, release_id, COALESCE(topology_fingerprint, ''), state, fence_epoch,
       operation_id, request_fingerprint, drain_deadline, authority_revoked, reclaim_started, updated_at
FROM runtime_target_retirement
WHERE TRIM(tenant_id) <> '' AND TRIM(deployment_id) <> '' AND TRIM(target_profile_id) <> ''
    AND TRIM(release_id) <> '';

INSERT INTO runtime_release_retirement_turn
    (permit_id, tenant_id, deployment_id, target_profile_id, release_id, fence_epoch, conversation_id, owner_id, created_at)
SELECT legacy_turn.permit_id, gate.tenant_id, gate.deployment_id, gate.target_profile_id, gate.release_id,
       legacy_turn.fence_epoch, legacy_turn.conversation_id, legacy_turn.owner_id, legacy_turn.created_at
FROM runtime_target_retirement_turn legacy_turn
CROSS JOIN runtime_release_retirement_gate gate;
