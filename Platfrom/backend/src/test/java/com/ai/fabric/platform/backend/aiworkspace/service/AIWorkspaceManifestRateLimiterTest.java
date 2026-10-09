package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AIWorkspaceManifestRateLimiterTest {

    @Test
    void appliesTheConfiguredLimitIndependentlyPerBoundedKey() {
        var properties = new PlatformAIWorkspaceProperties(
            Path.of("."), false, 2, Duration.ofSeconds(60), null, null);
        var limiter = new AIWorkspaceManifestRateLimiter(properties);

        assertThat(limiter.allow("client-a")).isTrue();
        assertThat(limiter.allow("client-a")).isTrue();
        assertThat(limiter.allow("client-a")).isFalse();
        assertThat(limiter.allow("client-b")).isTrue();
    }
}
