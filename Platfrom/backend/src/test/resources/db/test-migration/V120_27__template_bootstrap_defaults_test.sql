ALTER TABLE platform_deployments ADD COLUMN template_defaults_json TEXT;
UPDATE platform_deployments SET approval_required_for_apply = false;

CREATE TABLE platform_release_authorizations (
    release_id VARCHAR(64) PRIMARY KEY REFERENCES platform_deployment_releases(id) ON DELETE CASCADE,
    customer_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    deployment_id VARCHAR(64) NOT NULL,
    target_profile_id VARCHAR(64) NOT NULL,
    version_id VARCHAR(64) NOT NULL REFERENCES platform_deployment_versions(id),
    version_config_hash VARCHAR(64) NOT NULL,
    source_artifact_id VARCHAR(64),
    operation_key VARCHAR(128) NOT NULL,
    actor_id VARCHAR(256) NOT NULL,
    provider_usage_mode VARCHAR(32) NOT NULL,
    operations_mode VARCHAR(16) NOT NULL,
    decision VARCHAR(32) NOT NULL,
    reason VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    decision_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (provider_usage_mode IN ('TEAM_AUTHORIZED','CUSTOMER_KEY','FUNDED')),
    CHECK (operations_mode IN ('DISABLED','OBSERVE','ENFORCE')),
    CHECK (decision IN ('ALLOWED','BYPASSED','OBSERVED'))
);
