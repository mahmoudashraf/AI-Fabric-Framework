package com.easyluxury.ai.config;

import com.ai.infrastructure.config.AIProviderConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Easy Luxury specific AI configuration
 * 
 * This class provides Easy Luxury specific AI configuration that extends
 * the generic AI infrastructure module with project-specific settings.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Configuration
public class EasyLuxuryAIConfig {
    
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
    }
}