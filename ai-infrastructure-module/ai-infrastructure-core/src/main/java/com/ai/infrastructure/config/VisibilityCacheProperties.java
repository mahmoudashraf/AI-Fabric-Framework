package com.ai.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Properties controlling the optional visibility cache that front-loads async writes.
 */
@Data
@ConfigurationProperties(prefix = "ai.indexing.visibility-cache")
public class VisibilityCacheProperties {

    private boolean enabled = true;
    private Duration ttl = Duration.ofMinutes(10);
    private Duration cleanupInterval = Duration.ofMinutes(1);
    private int maxEntries = 10_000;
}
