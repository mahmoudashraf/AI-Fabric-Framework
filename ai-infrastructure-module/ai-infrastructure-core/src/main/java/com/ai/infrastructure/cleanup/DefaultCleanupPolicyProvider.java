package com.ai.infrastructure.cleanup;

import com.ai.infrastructure.config.AICleanupProperties;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Default implementation that reads its configuration from {@link AICleanupProperties}.
 */
public class DefaultCleanupPolicyProvider implements CleanupPolicyProvider {

    private final AICleanupProperties properties;

    public DefaultCleanupPolicyProvider(AICleanupProperties properties) {
        this.properties = properties;
    }

    @Override
    public CleanupStrategy getStrategy(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return CleanupStrategy.SOFT_DELETE;
        }
        return properties.getStrategies()
            .getOrDefault(entityType.toLowerCase(Locale.ROOT), CleanupStrategy.SOFT_DELETE);
    }

    @Override
    public int getRetentionDays(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return properties.getRetentionDays().getOrDefault("default", 180);
        }
        return properties.getRetentionDays()
            .getOrDefault(entityType.toLowerCase(Locale.ROOT), properties.getRetentionDays().getOrDefault("default", 180));
    }
}
