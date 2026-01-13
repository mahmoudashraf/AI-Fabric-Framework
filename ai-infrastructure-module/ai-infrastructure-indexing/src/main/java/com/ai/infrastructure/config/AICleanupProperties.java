package com.ai.infrastructure.config;

import com.ai.infrastructure.cleanup.CleanupStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Configuration for entity cleanup tasks (orphan detection, retention, etc.).
 */
@Data
@ConfigurationProperties(prefix = "ai.cleanup")
public class AICleanupProperties {

    private boolean enabled = true;
    private ScheduleProperties orphanedEntities = new ScheduleProperties("0 0 4 * * SUN");
    private NoVectorProperties noVectorEntities = new NoVectorProperties();
    private String retentionCron = "0 30 3 * * *";

    /**
     * Strategy mapping per entity type (defaults mirror the guide).
     */
    private Map<String, CleanupStrategy> strategies = new LinkedHashMap<>(Map.of(
        "order", CleanupStrategy.ARCHIVE,
        "user", CleanupStrategy.ARCHIVE,
        "product", CleanupStrategy.SOFT_DELETE,
        "behavior", CleanupStrategy.HARD_DELETE,
        "analytics", CleanupStrategy.HARD_DELETE
    ));

    /**
     * Retention windows (in days) per entity type.
     */
    private Map<String, Integer> retentionDays = new LinkedHashMap<>(Map.of(
        "order", 2555,
        "user", 365,
        "product", 180,
        "behavior", 90,
        "analytics", 30,
        "default", 180
    ));

    @Data
    public static class ScheduleProperties {
        private boolean enabled = true;
        private String cron;

        public ScheduleProperties() {
            this("0 0 4 * * SUN");
        }

        public ScheduleProperties(String cron) {
            this.cron = cron;
        }
    }

    @Data
    public static class NoVectorProperties extends ScheduleProperties {
        private Duration retention = Duration.ofHours(24);

        public NoVectorProperties() {
            super("0 0 5 * * SUN");
        }
    }
}
