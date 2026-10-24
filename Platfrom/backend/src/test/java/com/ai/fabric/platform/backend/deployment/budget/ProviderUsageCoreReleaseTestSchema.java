package com.ai.fabric.platform.backend.deployment.budget;

import org.springframework.jdbc.core.JdbcTemplate;

final class ProviderUsageCoreReleaseTestSchema {
    private ProviderUsageCoreReleaseTestSchema() { }

    static void create(JdbcTemplate jdbc) {
        jdbc.execute("""
            CREATE TABLE platform_release_authorizations (
              release_id VARCHAR(64) PRIMARY KEY, customer_id VARCHAR(64), tenant_id VARCHAR(64), deployment_id VARCHAR(64),
              target_profile_id VARCHAR(64), version_id VARCHAR(64), version_config_hash VARCHAR(255), source_artifact_id VARCHAR(64),
              operation_key VARCHAR(128), actor_id VARCHAR(255), provider_usage_mode VARCHAR(32), operations_mode VARCHAR(32),
              decision VARCHAR(32), reason VARCHAR(255), request_fingerprint VARCHAR(255), decision_json TEXT,
              created_at TIMESTAMP WITH TIME ZONE)
            """);
    }

    static void authorize(JdbcTemplate jdbc, String deploymentId, String mode) {
        jdbc.update("""
            INSERT INTO platform_release_authorizations
              (release_id,customer_id,tenant_id,deployment_id,target_profile_id,version_id,version_config_hash,source_artifact_id,
               operation_key,actor_id,provider_usage_mode,operations_mode,decision,reason,request_fingerprint,decision_json,created_at)
              VALUES (?,?,?,?,?,?,?,?,?,?,?,'DISABLED','BYPASSED','DISABLED_MANUAL_TEAM',?,'{}',CURRENT_TIMESTAMP)
            """, "release-" + deploymentId, "customer", "tenant", deploymentId, "target", "version-" + deploymentId,
            "config-hash", "artifact", "apply-" + deploymentId, "staff", mode, "a".repeat(64));
    }
}
