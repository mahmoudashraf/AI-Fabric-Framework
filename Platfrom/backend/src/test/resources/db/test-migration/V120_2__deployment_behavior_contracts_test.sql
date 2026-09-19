-- H2 test-schema mirror of production V132. Production continues to execute
-- V121-V133 in order on PostgreSQL; this keeps the H2 application-context
-- suite aligned without running PostgreSQL-only infrastructure data updates.
ALTER TABLE platform_deployments
    ADD COLUMN behavior_type VARCHAR(64) NOT NULL DEFAULT 'CONVERSATIONAL';

ALTER TABLE platform_deployment_drafts
    ADD COLUMN behavior_config_json TEXT NOT NULL DEFAULT '{"schemaVersion":"loomai-deployment-behavior-v1","type":"CONVERSATIONAL","contractVersion":1}';

ALTER TABLE platform_deployment_versions
    ADD COLUMN behavior_config_json TEXT NOT NULL DEFAULT '{"schemaVersion":"loomai-deployment-behavior-v1","type":"CONVERSATIONAL","contractVersion":1}';

ALTER TABLE platform_deployment_versions
    ADD COLUMN composition_provenance_json TEXT NOT NULL DEFAULT '{}';

ALTER TABLE deployment_source_artifacts
    ADD COLUMN capability_manifest_json TEXT NOT NULL DEFAULT '{}';

ALTER TABLE deployment_source_artifacts
    ADD COLUMN capability_manifest_hash VARCHAR(128);

CREATE INDEX idx_platform_deployments_behavior_type
    ON platform_deployments (behavior_type, status);
