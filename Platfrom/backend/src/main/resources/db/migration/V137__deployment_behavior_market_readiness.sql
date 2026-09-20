CREATE TABLE platform_deployment_behavior_readiness (
    id VARCHAR(64) PRIMARY KEY,
    behavior_type VARCHAR(64) NOT NULL,
    template_plugin_id VARCHAR(64) NOT NULL,
    template_plugin_version_id VARCHAR(64) NOT NULL,
    template_plugin_version VARCHAR(64) NOT NULL,
    composition_hash VARCHAR(128) NOT NULL,
    material_hash VARCHAR(128) NOT NULL UNIQUE,
    framework_version VARCHAR(32) NOT NULL,
    source_artifact_id VARCHAR(64) NOT NULL,
    source_commit VARCHAR(128) NOT NULL,
    image_digest VARCHAR(255) NOT NULL,
    source_capability_manifest_hash VARCHAR(128) NOT NULL,
    verification_pack_ids_json TEXT NOT NULL,
    maturity VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    hosted_proofs_json TEXT NOT NULL,
    approval_evidence_json TEXT NOT NULL,
    deployment_id VARCHAR(64) NOT NULL,
    deployment_version_id VARCHAR(64) NOT NULL,
    release_id VARCHAR(64) NOT NULL,
    evaluated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    approved_by_actor_id VARCHAR(255),
    approved_at TIMESTAMP WITH TIME ZONE,
    approval_note TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_behavior_readiness_behavior
    ON platform_deployment_behavior_readiness (behavior_type, status, updated_at);

CREATE INDEX idx_behavior_readiness_template
    ON platform_deployment_behavior_readiness (template_plugin_id, template_plugin_version, status);

CREATE INDEX idx_behavior_readiness_release
    ON platform_deployment_behavior_readiness (release_id, deployment_version_id);
