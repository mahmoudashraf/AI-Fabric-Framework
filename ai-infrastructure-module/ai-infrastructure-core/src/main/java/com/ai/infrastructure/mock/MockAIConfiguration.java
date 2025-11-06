package com.ai.infrastructure.mock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Restricts the mock AI beans to non-production profiles unless explicitly enabled.
 */
@Configuration
@Profile({"dev", "test"})
@ConditionalOnProperty(prefix = "ai.mock", name = "enabled", havingValue = "true")
public class MockAIConfiguration {
    // Marker configuration to document the profile/flag requirements.
}
