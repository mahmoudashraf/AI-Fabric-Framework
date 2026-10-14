CREATE TABLE runtime_target_generation_transition (
    operation_id VARCHAR(160) PRIMARY KEY,
    request_fingerprint VARCHAR(128) NOT NULL,
    payload_digest VARCHAR(64) NOT NULL,
    previous_deployment_id VARCHAR(128) NOT NULL,
    previous_target_profile_id VARCHAR(128) NOT NULL,
    previous_release_id VARCHAR(128) NOT NULL,
    next_release_id VARCHAR(128) NOT NULL,
    previous_fence_epoch BIGINT NOT NULL,
    next_fence_epoch BIGINT NOT NULL,
    previous_topology_fingerprint VARCHAR(64) NOT NULL,
    next_topology_fingerprint VARCHAR(64) NOT NULL,
    provider_stop_evidence_reference VARCHAR(1000) NOT NULL,
    result_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_runtime_generation_transition_release
    ON runtime_target_generation_transition (previous_release_id, next_release_id);
