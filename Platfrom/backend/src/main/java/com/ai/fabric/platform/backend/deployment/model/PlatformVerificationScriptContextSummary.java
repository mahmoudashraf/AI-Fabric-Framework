package com.ai.fabric.platform.backend.deployment.model;

import java.time.Duration;
import java.util.Map;

public record PlatformVerificationScriptContextSummary(
    String scriptPath,
    Map<String, String> environment,
    Map<String, String> secretEnvironment,
    Duration timeoutOverride,
    Integer maxOutputCharactersOverride
) {

    public PlatformVerificationScriptContextSummary(String scriptPath,
                                                    Map<String, String> environment,
                                                    Map<String, String> secretEnvironment) {
        this(scriptPath, environment, secretEnvironment, null, null);
    }
}
