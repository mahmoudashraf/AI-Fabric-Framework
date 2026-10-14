package com.ai.fabric.platform.backend.deployment.authority;

import java.util.List;
import java.util.Map;

/** Safe projection: fingerprints cover this nonsecret metadata, never secret bytes or their hashes. */
public record RuntimeAuthorityBinding(
    String customerId, String tenantId, String deploymentId, String targetProfileId, String versionId, String versionConfigHash,
    long generation, String privateIssuer, String privateAudience, String publicIssuer, String publicAudience,
    boolean anonymousBootstrapEnabled, boolean allowAnonymousSessions, List<String> allowedOrigins,
    Map<RuntimeAuthorityPurpose, SecretRevision> secrets
) {
    public RuntimeAuthorityBinding {
        allowedOrigins = List.copyOf(allowedOrigins);
        secrets = Map.copyOf(secrets);
    }

    public record SecretRevision(String reference, String revision) { }

    public record Snapshot(String snapshotId, String releaseId, String fingerprint, RuntimeAuthorityBinding binding) { }

    public record EnvironmentPlan(Map<String, String> runtime, Map<String, String> connector) {
        public EnvironmentPlan {
            runtime = Map.copyOf(runtime);
            connector = Map.copyOf(connector);
        }
    }
}
