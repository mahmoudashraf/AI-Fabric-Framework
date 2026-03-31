CREATE TABLE platform_deployments (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    environment_name VARCHAR(64) NOT NULL,
    template_id VARCHAR(128) NOT NULL,
    status VARCHAR(64) NOT NULL,
    active_draft_id VARCHAR(64),
    active_version_id VARCHAR(64),
    runtime_base_url TEXT,
    connector_base_url TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE platform_deployment_drafts (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL,
    revision_number INTEGER NOT NULL,
    status VARCHAR(64) NOT NULL,
    actions_config_json TEXT NOT NULL,
    entity_config_json TEXT NOT NULL,
    routing_config_json TEXT NOT NULL,
    provider_config_json TEXT NOT NULL,
    security_config_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_deployment_drafts_deployment
        FOREIGN KEY (deployment_id) REFERENCES platform_deployments (id) ON DELETE CASCADE
);

CREATE TABLE platform_deployment_versions (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL,
    source_draft_id VARCHAR(64) NOT NULL,
    version_label VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    config_hash VARCHAR(128) NOT NULL,
    reindex_required BOOLEAN NOT NULL,
    actions_config_json TEXT NOT NULL,
    entity_config_json TEXT NOT NULL,
    routing_config_json TEXT NOT NULL,
    provider_config_json TEXT NOT NULL,
    security_config_json TEXT NOT NULL,
    actions_artifact_yaml TEXT NOT NULL,
    entity_artifact_yaml TEXT NOT NULL,
    routing_artifact_yaml TEXT NOT NULL,
    manifest_json TEXT NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_deployment_versions_deployment
        FOREIGN KEY (deployment_id) REFERENCES platform_deployments (id) ON DELETE CASCADE,
    CONSTRAINT fk_platform_deployment_versions_source_draft
        FOREIGN KEY (source_draft_id) REFERENCES platform_deployment_drafts (id)
);

CREATE TABLE platform_deployment_releases (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL,
    deployment_version_id VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    verification_status VARCHAR(64) NOT NULL,
    provisioning_status VARCHAR(64) NOT NULL,
    provisioning_target VARCHAR(64) NOT NULL,
    current_step_key VARCHAR(128),
    current_step_description VARCHAR(512),
    error_message TEXT,
    provisioning_details_json TEXT NOT NULL,
    verification_run_id VARCHAR(64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    applied_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_deployment_releases_deployment
        FOREIGN KEY (deployment_id) REFERENCES platform_deployments (id) ON DELETE CASCADE,
    CONSTRAINT fk_platform_deployment_releases_version
        FOREIGN KEY (deployment_version_id) REFERENCES platform_deployment_versions (id)
);

CREATE TABLE platform_deployment_verification_runs (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL,
    release_id VARCHAR(64) NOT NULL,
    deployment_version_id VARCHAR(64) NOT NULL,
    verification_type VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    summary_message VARCHAR(1024) NOT NULL,
    checks_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_deployment_verification_runs_deployment
        FOREIGN KEY (deployment_id) REFERENCES platform_deployments (id) ON DELETE CASCADE,
    CONSTRAINT fk_platform_deployment_verification_runs_release
        FOREIGN KEY (release_id) REFERENCES platform_deployment_releases (id) ON DELETE CASCADE,
    CONSTRAINT fk_platform_deployment_verification_runs_version
        FOREIGN KEY (deployment_version_id) REFERENCES platform_deployment_versions (id)
);

CREATE TABLE platform_secrets (
    name VARCHAR(128) PRIMARY KEY,
    secret_value TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE UNIQUE INDEX uk_platform_deployment_drafts_deployment_revision
    ON platform_deployment_drafts (deployment_id, revision_number);

CREATE UNIQUE INDEX uk_platform_deployment_versions_deployment_label
    ON platform_deployment_versions (deployment_id, version_label);

CREATE INDEX idx_platform_deployment_drafts_deployment
    ON platform_deployment_drafts (deployment_id);

CREATE INDEX idx_platform_deployment_versions_deployment_published
    ON platform_deployment_versions (deployment_id, published_at);

CREATE INDEX idx_platform_deployment_releases_deployment_created
    ON platform_deployment_releases (deployment_id, created_at);

CREATE INDEX idx_platform_deployment_releases_deployment_version_created
    ON platform_deployment_releases (deployment_id, deployment_version_id, created_at);

CREATE INDEX idx_platform_deployment_verification_runs_deployment_created
    ON platform_deployment_verification_runs (deployment_id, created_at);

CREATE INDEX idx_platform_deployment_verification_runs_release
    ON platform_deployment_verification_runs (release_id);
