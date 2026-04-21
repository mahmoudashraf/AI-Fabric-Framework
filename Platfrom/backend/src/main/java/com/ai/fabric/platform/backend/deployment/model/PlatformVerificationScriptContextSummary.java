package com.ai.fabric.platform.backend.deployment.model;

import java.util.Map;

public record PlatformVerificationScriptContextSummary(
    String scriptPath,
    Map<String, String> environment,
    Map<String, String> secretEnvironment
) {
}
