package com.easyluxury.ai.config;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.service.AIConfigurationService;
import com.ai.infrastructure.health.AIHealthIndicator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Easy Luxury specific AI configuration
 * 
 * This class provides Easy Luxury specific AI configuration that extends
 * the generic AI infrastructure module with project-specific settings.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class EasyLuxuryAIConfig {
    
    private final AIProviderConfig aiProviderConfig;
    private final AIServiceConfig aiServiceConfig;
    private final AIConfigurationService aiConfigurationService;
    private final AIHealthIndicator aiHealthIndicator;
    
    @Bean
    @ConfigurationProperties(prefix = "easyluxury.ai")
    public EasyLuxuryAISettings easyLuxuryAISettings() {
        return new EasyLuxuryAISettings();
    }
    
    /**
     * Easy Luxury specific AI settings
     */
    public static class EasyLuxuryAISettings {
        private String productIndexName = "easyluxury-products";
        private String userIndexName = "easyluxury-users";
        private String orderIndexName = "easyluxury-orders";
        private Boolean enableProductRecommendations = true;
        private Boolean enableUserBehaviorTracking = true;
        private Boolean enableSmartValidation = true;
        private Boolean enableAIContentGeneration = true;
        private Boolean enableAISearch = true;
        private Boolean enableAIRAG = true;
        private String defaultAIModel = "gpt-4o-mini";
        private String defaultEmbeddingModel = "text-embedding-3-small";
        private Integer maxTokens = 2000;
        private Double temperature = 0.3;
        private Long timeoutSeconds = 60L;
        
        // Getters and setters
        public String getProductIndexName() { return productIndexName; }
        public void setProductIndexName(String productIndexName) { this.productIndexName = productIndexName; }
        
        public String getUserIndexName() { return userIndexName; }
        public void setUserIndexName(String userIndexName) { this.userIndexName = userIndexName; }
        
        public String getOrderIndexName() { return orderIndexName; }
        public void setOrderIndexName(String orderIndexName) { this.orderIndexName = orderIndexName; }
        
        public Boolean getEnableProductRecommendations() { return enableProductRecommendations; }
        public void setEnableProductRecommendations(Boolean enableProductRecommendations) { this.enableProductRecommendations = enableProductRecommendations; }
        
        public Boolean getEnableUserBehaviorTracking() { return enableUserBehaviorTracking; }
        public void setEnableUserBehaviorTracking(Boolean enableUserBehaviorTracking) { this.enableUserBehaviorTracking = enableUserBehaviorTracking; }
        
        public Boolean getEnableSmartValidation() { return enableSmartValidation; }
        public void setEnableSmartValidation(Boolean enableSmartValidation) { this.enableSmartValidation = enableSmartValidation; }
        
        public Boolean getEnableAIContentGeneration() { return enableAIContentGeneration; }
        public void setEnableAIContentGeneration(Boolean enableAIContentGeneration) { this.enableAIContentGeneration = enableAIContentGeneration; }
        
        public Boolean getEnableAISearch() { return enableAISearch; }
        public void setEnableAISearch(Boolean enableAISearch) { this.enableAISearch = enableAISearch; }
        
        public Boolean getEnableAIRAG() { return enableAIRAG; }
        public void setEnableAIRAG(Boolean enableAIRAG) { this.enableAIRAG = enableAIRAG; }
        
        public String getDefaultAIModel() { return defaultAIModel; }
        public void setDefaultAIModel(String defaultAIModel) { this.defaultAIModel = defaultAIModel; }
        
        public String getDefaultEmbeddingModel() { return defaultEmbeddingModel; }
        public void setDefaultEmbeddingModel(String defaultEmbeddingModel) { this.defaultEmbeddingModel = defaultEmbeddingModel; }
        
        public Integer getMaxTokens() { return maxTokens; }
        public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
        
        public Double getTemperature() { return temperature; }
        public void setTemperature(Double temperature) { this.temperature = temperature; }
        
        public Long getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(Long timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }
}