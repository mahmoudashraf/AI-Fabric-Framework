package com.ai.infrastructure.relationship.config;

import com.ai.infrastructure.relationship.model.QueryMode;
import com.ai.infrastructure.relationship.model.ReturnMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for {@code ai.infrastructure.relationship.*} namespace.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.infrastructure.relationship")
public class RelationshipQueryProperties {

    /**
     * Enables the relationship-aware query module. Auto-detected unless explicitly disabled.
     */
    private boolean enabled = true;

    /**
     * Whether semantic/vector ranking is permitted. The planner can still decide not to use it.
     */
    private boolean enableVectorSearch = true;

    /**
     * Fallback to metadata traversal when JPA relationships are not available.
     */
    private boolean fallbackToMetadata = true;

    /**
     * Fallback to semantic ranking when relationship traversal produces insufficient recall.
     */
    private boolean fallbackToVectorSearch = true;

    /**
     * Enables basic validation (entity/relationship guards) before executing JPQL.
     */
    private boolean enableQueryValidation = true;

    /**
     * Enables plan/result caching.
     */
    private boolean enableQueryCaching = true;

    /**
     * TTL for cached plans/results (seconds). Configured explicitly to match implementation plan.
     */
    @Positive
    private long queryCacheTtlSeconds = 3600;

    @Min(1)
    @Max(5)
    private int maxTraversalDepth = 3;

    @Min(0)
    @Max(1)
    private double defaultSimilarityThreshold = 0.7;

    @NotNull
    private ReturnMode defaultReturnMode = ReturnMode.IDS;

    /**
     * Default mode when callers do not override (LLM auto-detection will flip to ENHANCED only when needed).
     */
    @NotNull
    private QueryMode defaultQueryMode = QueryMode.STANDALONE;

    @NestedConfigurationProperty
    private final LlmProperties llm = new LlmProperties();

    @NestedConfigurationProperty
    private final SchemaProperties schema = new SchemaProperties();

    @NestedConfigurationProperty
    private final PlannerProperties planner = new PlannerProperties();
    @NestedConfigurationProperty
    private final CacheProperties cache = new CacheProperties();

    @Getter
    public static class PlannerProperties {
        private boolean logPlans = false;

        @Min(0)
        @Max(1)
        private double minConfidenceToExecute = 0.55;

        public void setLogPlans(boolean logPlans) {
            this.logPlans = logPlans;
        }

        public void setMinConfidenceToExecute(double minConfidenceToExecute) {
            if (minConfidenceToExecute < 0) {
                this.minConfidenceToExecute = 0;
            } else if (minConfidenceToExecute > 1) {
                this.minConfidenceToExecute = 1;
            } else {
                this.minConfidenceToExecute = minConfidenceToExecute;
            }
        }
    }

    @Getter
    public static class LlmProperties {
        /**
         * Preferred reasoning model. Empty value defers to {@link com.ai.infrastructure.core.AICoreService} defaults.
         */
        private String model;

        @Min(0)
        @Max(1)
        private double temperature = 0.1;

        @Positive
        private int maxRetries = 3;

        @Positive
        private int timeoutSeconds = 30;

        @Min(0)
        @Max(1)
        private double minConfidence = 0.6;

        public void setModel(String model) {
            this.model = (model == null || model.isBlank()) ? null : model.trim();
        }

        public void setTemperature(double temperature) {
            this.temperature = clampProbability(temperature);
        }

        public void setMinConfidence(double confidence) {
            this.minConfidence = clampProbability(confidence);
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = Math.max(1, maxRetries);
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = Math.max(1, timeoutSeconds);
        }

        private double clampProbability(double value) {
            if (value < 0) {
                return 0;
            }
            if (value > 1) {
                return 1;
            }
            return value;
        }
    }

    @Getter
    public static class CacheProperties {
        private boolean enabled = true;
        @NestedConfigurationProperty
        private final RegionProperties plan = new RegionProperties(3600, 10_000);
        @NestedConfigurationProperty
        private final RegionProperties embedding = new RegionProperties(86_400, 50_000);
        @NestedConfigurationProperty
        private final RegionProperties result = new RegionProperties(1_800, 5_000);

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    @Getter
    public static class RegionProperties {
        private long ttlSeconds;
        private int maxEntries;

        public RegionProperties() {
            this(3_600, 10_000);
        }

        public RegionProperties(long ttlSeconds, int maxEntries) {
            this.ttlSeconds = ttlSeconds;
            this.maxEntries = maxEntries;
        }

        public void setTtlSeconds(long ttlSeconds) {
            this.ttlSeconds = Math.max(1, ttlSeconds);
        }

        public void setMaxEntries(int maxEntries) {
            this.maxEntries = Math.max(1, maxEntries);
        }

        public long ttlMillis() {
            return ttlSeconds * 1000;
        }
    }

    @Getter
    public static class SchemaProperties {
        /**
         * Automatically introspect @AICapable entities on startup.
         */
        private boolean autoDiscover = true;

        /**
         * Rebuild cached schema on every startup.
         */
        private boolean refreshOnStartup = true;

        /**
         * Log discovered schema for troubleshooting (disabled by default to avoid noise).
         */
        private boolean logSchema = false;

        /**
         * Include field metadata (non-relationships) in the schema payload.
         */
        private boolean includeFields = true;

        public void setAutoDiscover(boolean autoDiscover) {
            this.autoDiscover = autoDiscover;
        }

        public void setRefreshOnStartup(boolean refreshOnStartup) {
            this.refreshOnStartup = refreshOnStartup;
        }

        public void setLogSchema(boolean logSchema) {
            this.logSchema = logSchema;
        }

        public void setIncludeFields(boolean includeFields) {
            this.includeFields = includeFields;
        }
    }
}
