package com.ai.fabric.platform.backend.deployment.model;

import java.util.LinkedHashMap;
import java.util.Map;

public record DeploymentBehaviorVerificationExpectationOverrides(
    String behaviorType,
    String templatePluginId,
    String templatePluginVersion,
    String targetProfileId,
    String sourceArtifactId,
    String environment,
    boolean keepDeployment
) {
    public Map<String, String> toEnvironmentOverrides() {
        Map<String, String> result = new LinkedHashMap<>();
        put(result, "BEHAVIOR_TYPE", behaviorType);
        put(result, "TEMPLATE_PLUGIN_ID", templatePluginId);
        put(result, "TEMPLATE_PLUGIN_VERSION", templatePluginVersion);
        put(result, "TARGET_PROFILE_ID", targetProfileId);
        put(result, "SOURCE_ARTIFACT_ID", sourceArtifactId);
        put(result, "VALIDATION_ENVIRONMENT", environment);
        result.put("KEEP_DEPLOYMENT", Boolean.toString(keepDeployment));
        return Map.copyOf(result);
    }

    private static void put(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }
}
