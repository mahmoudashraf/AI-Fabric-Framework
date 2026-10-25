CREATE TABLE deployment_lifecycle_grants (
    grant_id VARCHAR(64) PRIMARY KEY,
    operation_id VARCHAR(128) NOT NULL UNIQUE,
    deployment_id VARCHAR(64) NOT NULL REFERENCES platform_deployments(id),
    target_profile_id VARCHAR(64) NOT NULL REFERENCES deployment_target_profiles(id),
    release_id VARCHAR(64) NOT NULL REFERENCES platform_deployment_releases(id),
    scope_hash VARCHAR(64) NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    request_json TEXT NOT NULL,
    snapshot_json TEXT NOT NULL,
    actor_id VARCHAR(255) NOT NULL,
    state VARCHAR(24) NOT NULL,
    claimed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX deployment_lifecycle_claim_idx ON deployment_lifecycle_grants(deployment_id, target_profile_id, claimed);
CREATE TABLE deployment_lifecycle_commands (
    command_id VARCHAR(128) PRIMARY KEY,
    grant_id VARCHAR(64) NOT NULL REFERENCES deployment_lifecycle_grants(grant_id),
    operation_id VARCHAR(128) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    request_digest VARCHAR(64) NOT NULL,
    request_json TEXT NOT NULL,
    receipt_json TEXT NOT NULL,
    status VARCHAR(24) NOT NULL,
    worker_token VARCHAR(64),
    lease_until TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(grant_id, phase)
);
CREATE INDEX deployment_lifecycle_command_work_idx ON deployment_lifecycle_commands(status, lease_until);
CREATE TABLE deployment_lifecycle_effects (
    command_id VARCHAR(128) NOT NULL REFERENCES deployment_lifecycle_commands(command_id),
    effect_id VARCHAR(255) NOT NULL,
    state VARCHAR(24) NOT NULL,
    provider_operation_id VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY(command_id, effect_id)
);
