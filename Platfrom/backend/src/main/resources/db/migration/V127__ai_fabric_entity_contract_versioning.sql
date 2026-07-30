ALTER TABLE platform_deployment_drafts
    ADD COLUMN entity_config_contract_version VARCHAR(64) NOT NULL DEFAULT 'AI_ENTITY_CONFIG_V0_3';

ALTER TABLE platform_deployment_versions
    ADD COLUMN entity_config_contract_version VARCHAR(64) NOT NULL DEFAULT 'AI_ENTITY_CONFIG_V0_3';

ALTER TABLE platform_deployment_versions
    ADD COLUMN ai_fabric_framework_version VARCHAR(32) NOT NULL DEFAULT '0.3.1';

CREATE TABLE platform_entity_config_migration_audits (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL,
    draft_id VARCHAR(64) NOT NULL,
    source_contract_version VARCHAR(64) NOT NULL,
    target_contract_version VARCHAR(64) NOT NULL,
    status VARCHAR(64) NOT NULL,
    before_hash VARCHAR(128) NOT NULL,
    after_hash VARCHAR(128),
    before_config_json TEXT NOT NULL,
    after_config_json TEXT,
    report_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_platform_entity_config_migration_audit_deployment
        FOREIGN KEY (deployment_id) REFERENCES platform_deployments (id) ON DELETE CASCADE
);

CREATE INDEX idx_platform_entity_config_migration_audit_draft
    ON platform_entity_config_migration_audits (draft_id, created_at);
