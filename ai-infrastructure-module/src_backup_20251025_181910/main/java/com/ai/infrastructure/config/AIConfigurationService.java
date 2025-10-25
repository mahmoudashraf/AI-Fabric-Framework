package com.ai.infrastructure.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
// import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AI Configuration Service
 * 
 * Dynamic configuration management service for AI infrastructure with
 * hot-reload support and environment-specific settings.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
// @RefreshScope
public class AIConfigurationService {
    
    private final AIServiceConfig aiServiceConfig;
    
    @Value("${ai.config.refresh-interval:300}")
    private long refreshIntervalSeconds;
    
    @Value("${ai.config.enable-hot-reload:true}")
    private boolean enableHotReload;
    
    private final Map<String, Object> dynamicConfig = new ConcurrentHashMap<>();
    private final Map<String, Long> configTimestamps = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;
    
    @PostConstruct
    public void initialize() {
        log.info("Initializing AI Configuration Service");
        
        // Load initial configuration
        loadConfiguration();
        
        // Setup hot-reload if enabled
        if (enableHotReload) {
            setupHotReload();
        }
        
        log.info("AI Configuration Service initialized successfully");
    }
    
    /**
     * Get configuration value by key
     */
    public <T> T getConfig(String key, Class<T> type) {
        return getConfig(key, type, null);
    }
    
    /**
     * Get configuration value by key with default
     */
    @SuppressWarnings("unchecked")
    public <T> T getConfig(String key, Class<T> type, T defaultValue) {
        Object value = dynamicConfig.get(key);
        
        if (value == null) {
            return defaultValue;
        }
        
        try {
            if (type.isInstance(value)) {
                return (T) value;
            } else if (type == String.class) {
                return (T) value.toString();
            } else if (type == Integer.class && value instanceof Number) {
                return (T) Integer.valueOf(((Number) value).intValue());
            } else if (type == Long.class && value instanceof Number) {
                return (T) Long.valueOf(((Number) value).longValue());
            } else if (type == Double.class && value instanceof Number) {
                return (T) Double.valueOf(((Number) value).doubleValue());
            } else if (type == Boolean.class && value instanceof Boolean) {
                return (T) value;
            }
        } catch (Exception e) {
            log.warn("Failed to convert config value for key {} to type {}", key, type.getSimpleName(), e);
        }
        
        return defaultValue;
    }
    
    /**
     * Set configuration value
     */
    public void setConfig(String key, Object value) {
        dynamicConfig.put(key, value);
        configTimestamps.put(key, System.currentTimeMillis());
        log.debug("Updated configuration: {} = {}", key, value);
    }
    
    /**
     * Remove configuration value
     */
    public void removeConfig(String key) {
        dynamicConfig.remove(key);
        configTimestamps.remove(key);
        log.debug("Removed configuration: {}", key);
    }
    
    /**
     * Check if configuration exists
     */
    public boolean hasConfig(String key) {
        return dynamicConfig.containsKey(key);
    }
    
    /**
     * Get all configuration keys
     */
    public java.util.Set<String> getConfigKeys() {
        return dynamicConfig.keySet();
    }
    
    /**
     * Get configuration timestamp
     */
    public Long getConfigTimestamp(String key) {
        return configTimestamps.get(key);
    }
    
    /**
     * Get AI service configuration
     */
    public AIServiceConfig getAIServiceConfig() {
        return aiServiceConfig;
    }
    
    /**
     * Validate configuration
     */
    public boolean validateConfiguration() {
        try {
            // Validate required configurations
            if (aiServiceConfig.getDefaultProvider() == null || aiServiceConfig.getDefaultProvider().isEmpty()) {
                log.error("Default AI provider is not configured");
                return false;
            }
            
            if (aiServiceConfig.getTimeout() == null) {
                log.error("Timeout configuration is missing");
                return false;
            }
            
            if (aiServiceConfig.getRetry() == null) {
                log.error("Retry configuration is missing");
                return false;
            }
            
            // Validate feature flags
            if (aiServiceConfig.getFeatures() == null) {
                log.error("Feature flags configuration is missing");
                return false;
            }
            
            log.info("Configuration validation successful");
            return true;
            
        } catch (Exception e) {
            log.error("Configuration validation failed", e);
            return false;
        }
    }
    
    /**
     * Reload configuration
     */
    public void reloadConfiguration() {
        log.info("Reloading AI configuration");
        loadConfiguration();
        log.info("AI configuration reloaded successfully");
    }
    
    /**
     * Get configuration summary
     */
    public Map<String, Object> getConfigurationSummary() {
        Map<String, Object> summary = new ConcurrentHashMap<>();
        
        summary.put("totalConfigs", dynamicConfig.size());
        summary.put("aiServiceEnabled", aiServiceConfig.getEnabled());
        summary.put("defaultProvider", aiServiceConfig.getDefaultProvider());
        summary.put("fallbackProvider", aiServiceConfig.getFallbackProvider());
        summary.put("hotReloadEnabled", enableHotReload);
        summary.put("refreshIntervalSeconds", refreshIntervalSeconds);
        
        if (aiServiceConfig.getFeatures() != null) {
            summary.put("features", Map.of(
                "rag", aiServiceConfig.getFeatures().getEnableRAG(),
                "embeddings", aiServiceConfig.getFeatures().getEnableEmbeddings(),
                "search", aiServiceConfig.getFeatures().getEnableSearch(),
                "generation", aiServiceConfig.getFeatures().getEnableGeneration(),
                "caching", aiServiceConfig.getFeatures().getEnableCaching(),
                "monitoring", aiServiceConfig.getFeatures().getEnableMonitoring()
            ));
        }
        
        return summary;
    }
    
    /**
     * Load configuration from various sources
     */
    private void loadConfiguration() {
        try {
            // Load from environment variables
            loadFromEnvironment();
            
            // Load from application properties
            loadFromProperties();
            
            // Load from external configuration sources
            loadFromExternalSources();
            
            log.debug("Loaded {} configuration entries", dynamicConfig.size());
            
        } catch (Exception e) {
            log.error("Failed to load configuration", e);
        }
    }
    
    /**
     * Load configuration from environment variables
     */
    private void loadFromEnvironment() {
        // Load AI-specific environment variables
        String[] envKeys = {
            "AI_DEFAULT_PROVIDER",
            "AI_FALLBACK_PROVIDER",
            "AI_TIMEOUT_SECONDS",
            "AI_MAX_RETRIES",
            "AI_RATE_LIMIT_ENABLED",
            "AI_CACHE_ENABLED",
            "AI_MONITORING_ENABLED"
        };
        
        for (String key : envKeys) {
            String value = System.getenv(key);
            if (value != null && !value.isEmpty()) {
                String configKey = key.toLowerCase().replace("_", ".");
                setConfig(configKey, value);
            }
        }
    }
    
    /**
     * Load configuration from application properties
     */
    private void loadFromProperties() {
        // Configuration is already loaded via @ConfigurationProperties
        // This method can be used for additional property processing
    }
    
    /**
     * Load configuration from external sources
     */
    private void loadFromExternalSources() {
        // Placeholder for loading from external configuration sources
        // like Consul, etcd, or other configuration management systems
    }
    
    /**
     * Setup hot-reload functionality
     */
    private void setupHotReload() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ai-config-reloader");
            t.setDaemon(true);
            return t;
        });
        
        scheduler.scheduleWithFixedDelay(
            this::reloadConfiguration,
            refreshIntervalSeconds,
            refreshIntervalSeconds,
            TimeUnit.SECONDS
        );
        
        log.info("Hot-reload enabled with {} second interval", refreshIntervalSeconds);
    }
    
    /**
     * Shutdown the configuration service
     */
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        log.info("AI Configuration Service shutdown completed");
    }
}