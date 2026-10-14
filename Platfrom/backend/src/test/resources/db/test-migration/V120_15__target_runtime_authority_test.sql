CREATE TABLE platform_runtime_target_authorities (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL REFERENCES platform_deployments(id),
    target_profile_id VARCHAR(64) NOT NULL REFERENCES deployment_target_profiles(id),
    customer_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    generation BIGINT NOT NULL CHECK (generation > 0),
    state VARCHAR(16) NOT NULL CHECK (state IN ('ACTIVE', 'REVOKED')),
    secret_revisions_json TEXT NOT NULL,
    secret_integrity_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (deployment_id, target_profile_id)
);
CREATE TABLE platform_runtime_authority_snapshots (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL REFERENCES platform_deployments(id),
    target_profile_id VARCHAR(64) NOT NULL REFERENCES deployment_target_profiles(id),
    version_id VARCHAR(64) NOT NULL REFERENCES platform_deployment_versions(id),
    release_id VARCHAR(64) UNIQUE REFERENCES platform_deployment_releases(id),
    fingerprint VARCHAR(64) NOT NULL,
    binding_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX ix_runtime_authority_candidate ON platform_runtime_authority_snapshots (deployment_id, target_profile_id, version_id);
