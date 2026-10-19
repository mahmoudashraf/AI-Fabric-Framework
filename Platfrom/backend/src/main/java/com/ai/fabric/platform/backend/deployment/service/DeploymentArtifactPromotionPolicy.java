package com.ai.fabric.platform.backend.deployment.service;

import org.springframework.util.StringUtils;

/** Production qualification may be exercised in staging, never the reverse. */
final class DeploymentArtifactPromotionPolicy {
    private DeploymentArtifactPromotionPolicy() { }

    static boolean allows(String promotedChannel, String expectedChannel) {
        if (!StringUtils.hasText(promotedChannel)) {
            return false;
        }
        if (expectedChannel == null || expectedChannel.equalsIgnoreCase(promotedChannel)) {
            return true;
        }
        return "staging".equalsIgnoreCase(expectedChannel)
            && "production".equalsIgnoreCase(promotedChannel);
    }
}
