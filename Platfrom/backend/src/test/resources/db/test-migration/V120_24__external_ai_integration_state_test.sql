-- H2 schema mirror only. PostgreSQL tests verify production migrations and filtered uniqueness.
ALTER TABLE platform_deployment_releases ADD COLUMN ai_state_storage_snapshot_json TEXT;
ALTER TABLE platform_deployment_releases ADD COLUMN ai_state_storage_fingerprint VARCHAR(64);

CREATE TABLE platform_managed_resource_ownership_mutex (id INTEGER PRIMARY KEY);
INSERT INTO platform_managed_resource_ownership_mutex(id) VALUES (1);
CREATE TABLE platform_managed_resource_ownership (
    id VARCHAR(64) PRIMARY KEY, location_key VARCHAR(64) NOT NULL UNIQUE,
    provider VARCHAR(32) NOT NULL, resource_kind VARCHAR(32) NOT NULL,
    parent_identity VARCHAR(1024) NOT NULL, resource_name VARCHAR(255) NOT NULL,
    customer_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,
    deployment_id VARCHAR(64) NOT NULL, target_profile_id VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL CHECK (state IN ('CREATE_PENDING','OWNED','DELETE_PENDING','DELETED')),
    resource_identity VARCHAR(2048), endpoint VARCHAR(2048),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL, updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_managed_ownership_evidence CHECK (
        (state = 'CREATE_PENDING' AND resource_identity IS NULL AND endpoint IS NULL)
        OR (state IN ('OWNED','DELETE_PENDING','DELETED') AND resource_identity IS NOT NULL))
);
CREATE TABLE deployment_ai_state_storage_approvals (
    binding_id VARCHAR(64) NOT NULL REFERENCES deployment_provider_resource_handles(id) ON DELETE CASCADE,
    version_id VARCHAR(64) NOT NULL REFERENCES platform_deployment_versions(id) ON DELETE CASCADE,
    config_hash VARCHAR(128) NOT NULL, binding_fingerprint VARCHAR(128) NOT NULL,
    approval_reference VARCHAR(255) NOT NULL, approved_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (binding_id, version_id)
);
CREATE TABLE deployment_ai_state_schema_claims (
    schema_identity VARCHAR(128) PRIMARY KEY, binding_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(64) NOT NULL, tenant_id VARCHAR(64) NOT NULL,
    deployment_id VARCHAR(64) NOT NULL, target_profile_id VARCHAR(64) NOT NULL
);
CREATE TABLE deployment_external_integration_approvals (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL REFERENCES platform_deployments(id),
    version_id VARCHAR(64) NOT NULL REFERENCES platform_deployment_versions(id),
    target_profile_id VARCHAR(64) NOT NULL REFERENCES deployment_target_profiles(id),
    operation_key VARCHAR(128) NOT NULL, evidence_json TEXT NOT NULL, integrity_mac VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (deployment_id, operation_key)
);
CREATE TABLE deployment_external_integration_bindings (
    id VARCHAR(64) PRIMARY KEY,
    approval_id VARCHAR(64) NOT NULL REFERENCES deployment_external_integration_approvals(id),
    deployment_id VARCHAR(64) NOT NULL REFERENCES platform_deployments(id),
    version_id VARCHAR(64) NOT NULL REFERENCES platform_deployment_versions(id),
    target_profile_id VARCHAR(64) NOT NULL REFERENCES deployment_target_profiles(id),
    slot_id VARCHAR(128) NOT NULL, evidence_json TEXT NOT NULL, integrity_mac VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL CHECK (status IN ('CONFIGURED','REVOKED')),
    revision BIGINT NOT NULL CHECK (revision >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (deployment_id, version_id, target_profile_id, slot_id)
);
CREATE TABLE deployment_external_integration_snapshots (
    release_id VARCHAR(64) PRIMARY KEY REFERENCES platform_deployment_releases(id),
    deployment_id VARCHAR(64) NOT NULL REFERENCES platform_deployments(id),
    version_id VARCHAR(64) NOT NULL REFERENCES platform_deployment_versions(id),
    target_profile_id VARCHAR(64) NOT NULL REFERENCES deployment_target_profiles(id),
    evidence_json TEXT NOT NULL, integrity_mac VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
