ALTER TABLE platform_deployments
    ADD COLUMN behavior_type VARCHAR(64) NOT NULL DEFAULT 'CONVERSATIONAL';

ALTER TABLE platform_deployment_drafts
    ADD COLUMN behavior_config_json TEXT NOT NULL DEFAULT '{"schemaVersion":"loomai-deployment-behavior-v1","type":"CONVERSATIONAL","contractVersion":1,"activation":{"sources":["AUTHENTICATED_INTERACTIVE"]},"channelBindings":["BACKEND_API","DOCKED_COMPOSER","MAX_MODE","INLINE_ASSISTANT","QUERY_ONCE"],"executionExtensions":[],"durability":{"mode":"OPTIONAL_BACKEND_SESSION"},"runtimeRequirements":{"capabilities":["ai-fabric-core","ai-fabric-chat-session"],"endpointClasses":["chat-query","query-once","session-management"],"migrationIds":[],"verificationPackIds":["conversational-behavior-v1"]},"authority":{"trustedContextRequired":true,"requestMaySelectIdentityOrAuthority":false,"automaticWritesAllowed":false}}';

ALTER TABLE platform_deployment_versions
    ADD COLUMN behavior_config_json TEXT NOT NULL DEFAULT '{"schemaVersion":"loomai-deployment-behavior-v1","type":"CONVERSATIONAL","contractVersion":1,"activation":{"sources":["AUTHENTICATED_INTERACTIVE"]},"channelBindings":["BACKEND_API","DOCKED_COMPOSER","MAX_MODE","INLINE_ASSISTANT","QUERY_ONCE"],"executionExtensions":[],"durability":{"mode":"OPTIONAL_BACKEND_SESSION"},"runtimeRequirements":{"capabilities":["ai-fabric-core","ai-fabric-chat-session"],"endpointClasses":["chat-query","query-once","session-management"],"migrationIds":[],"verificationPackIds":["conversational-behavior-v1"]},"authority":{"trustedContextRequired":true,"requestMaySelectIdentityOrAuthority":false,"automaticWritesAllowed":false}}';

ALTER TABLE platform_deployment_versions
    ADD COLUMN composition_provenance_json TEXT NOT NULL DEFAULT '{}';

ALTER TABLE deployment_source_artifacts
    ADD COLUMN capability_manifest_json TEXT NOT NULL DEFAULT '{}';

ALTER TABLE deployment_source_artifacts
    ADD COLUMN capability_manifest_hash VARCHAR(128);

CREATE INDEX idx_platform_deployments_behavior_type
    ON platform_deployments (behavior_type, status);
