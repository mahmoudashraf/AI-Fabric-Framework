package com.easyluxury.ai.config;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.AIConfigurationService;
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
        // Index Names
        private String productIndexName = "easyluxury-products";
        private String userIndexName = "easyluxury-users";
        private String orderIndexName = "easyluxury-orders";
        
        // Feature Toggles
        private Boolean enableProductRecommendations = true;
        private Boolean enableUserBehaviorTracking = true;
        private Boolean enableSmartValidation = true;
        private Boolean enableAIContentGeneration = true;
        private Boolean enableAISearch = true;
        private Boolean enableAIRAG = true;
        private Boolean enableProductDescriptionGeneration = true;
        private Boolean enableProductCategorization = true;
        private Boolean enableProductTagging = true;
        
        // AI Model Configuration
        private String defaultAIModel = "gpt-4o-mini";
        private String defaultEmbeddingModel = "text-embedding-3-small";
        private Integer maxTokens = 2000;
        private Double temperature = 0.3;
        private Long timeoutSeconds = 60L;
        
        // Product-Specific Settings
        private ProductSettings product = new ProductSettings();
        
        // User-Specific Settings
        private UserSettings user = new UserSettings();
        
        // Order-Specific Settings
        private OrderSettings order = new OrderSettings();
        
        /**
         * Product-specific AI settings
         */
        public static class ProductSettings {
            private DescriptionGeneration descriptionGeneration = new DescriptionGeneration();
            private Categorization categorization = new Categorization();
            private Tagging tagging = new Tagging();
            private Search search = new Search();
            private Recommendations recommendations = new Recommendations();
            
            public static class DescriptionGeneration {
                private Boolean enabled = true;
                private Integer maxLength = 500;
                private String style = "luxury";
                
                // Getters and setters
                public Boolean getEnabled() { return enabled; }
                public void setEnabled(Boolean enabled) { this.enabled = enabled; }
                public Integer getMaxLength() { return maxLength; }
                public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }
                public String getStyle() { return style; }
                public void setStyle(String style) { this.style = style; }
            }
            
            public static class Categorization {
                private Boolean enabled = true;
                private String categories = "luxury,premium,exclusive,limited-edition";
                
                // Getters and setters
                public Boolean getEnabled() { return enabled; }
                public void setEnabled(Boolean enabled) { this.enabled = enabled; }
                public String getCategories() { return categories; }
                public void setCategories(String categories) { this.categories = categories; }
            }
            
            public static class Tagging {
                private Boolean enabled = true;
                private Integer maxTags = 10;
                private String tagTypes = "material,style,occasion,price-range,season";
                
                // Getters and setters
                public Boolean getEnabled() { return enabled; }
                public void setEnabled(Boolean enabled) { this.enabled = enabled; }
                public Integer getMaxTags() { return maxTags; }
                public void setMaxTags(Integer maxTags) { this.maxTags = maxTags; }
                public String getTagTypes() { return tagTypes; }
                public void setTagTypes(String tagTypes) { this.tagTypes = tagTypes; }
            }
            
            public static class Search {
                private Boolean enabled = true;
                private Double similarityThreshold = 0.7;
                private Integer maxResults = 20;
                
                // Getters and setters
                public Boolean getEnabled() { return enabled; }
                public void setEnabled(Boolean enabled) { this.enabled = enabled; }
                public Double getSimilarityThreshold() { return similarityThreshold; }
                public void setSimilarityThreshold(Double similarityThreshold) { this.similarityThreshold = similarityThreshold; }
                public Integer getMaxResults() { return maxResults; }
                public void setMaxResults(Integer maxResults) { this.maxResults = maxResults; }
            }
            
            public static class Recommendations {
                private Boolean enabled = true;
                private Integer maxRecommendations = 10;
                private String algorithm = "collaborative-filtering";
                
                // Getters and setters
                public Boolean getEnabled() { return enabled; }
                public void setEnabled(Boolean enabled) { this.enabled = enabled; }
                public Integer getMaxRecommendations() { return maxRecommendations; }
                public void setMaxRecommendations(Integer maxRecommendations) { this.maxRecommendations = maxRecommendations; }
                public String getAlgorithm() { return algorithm; }
                public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
            }
            
            // Getters and setters
            public DescriptionGeneration getDescriptionGeneration() { return descriptionGeneration; }
            public void setDescriptionGeneration(DescriptionGeneration descriptionGeneration) { this.descriptionGeneration = descriptionGeneration; }
            public Categorization getCategorization() { return categorization; }
            public void setCategorization(Categorization categorization) { this.categorization = categorization; }
            public Tagging getTagging() { return tagging; }
            public void setTagging(Tagging tagging) { this.tagging = tagging; }
            public Search getSearch() { return search; }
            public void setSearch(Search search) { this.search = search; }
            public Recommendations getRecommendations() { return recommendations; }
            public void setRecommendations(Recommendations recommendations) { this.recommendations = recommendations; }
        }
        
        /**
         * User-specific AI settings
         */
        public static class UserSettings {
            private BehaviorTracking behaviorTracking = new BehaviorTracking();
            private Preferences preferences = new Preferences();
            private Recommendations recommendations = new Recommendations();
            
            public static class BehaviorTracking {
                private Boolean enabled = true;
                private Boolean trackViews = true;
                private Boolean trackPurchases = true;
                private Boolean trackSearches = true;
                private Integer retentionDays = 365;
                
                // Getters and setters
                public Boolean getEnabled() { return enabled; }
                public void setEnabled(Boolean enabled) { this.enabled = enabled; }
                public Boolean getTrackViews() { return trackViews; }
                public void setTrackViews(Boolean trackViews) { this.trackViews = trackViews; }
                public Boolean getTrackPurchases() { return trackPurchases; }
                public void setTrackPurchases(Boolean trackPurchases) { this.trackPurchases = trackPurchases; }
                public Boolean getTrackSearches() { return trackSearches; }
                public void setTrackSearches(Boolean trackSearches) { this.trackSearches = trackSearches; }
                public Integer getRetentionDays() { return retentionDays; }
                public void setRetentionDays(Integer retentionDays) { this.retentionDays = retentionDays; }
            }
            
            public static class Preferences {
                private Boolean learningEnabled = true;
                private String updateFrequency = "daily";
                private Integer minInteractions = 5;
                
                // Getters and setters
                public Boolean getLearningEnabled() { return learningEnabled; }
                public void setLearningEnabled(Boolean learningEnabled) { this.learningEnabled = learningEnabled; }
                public String getUpdateFrequency() { return updateFrequency; }
                public void setUpdateFrequency(String updateFrequency) { this.updateFrequency = updateFrequency; }
                public Integer getMinInteractions() { return minInteractions; }
                public void setMinInteractions(Integer minInteractions) { this.minInteractions = minInteractions; }
            }
            
            public static class Recommendations {
                private Boolean enabled = true;
                private Integer maxRecommendations = 15;
                private Double diversityFactor = 0.3;
                
                // Getters and setters
                public Boolean getEnabled() { return enabled; }
                public void setEnabled(Boolean enabled) { this.enabled = enabled; }
                public Integer getMaxRecommendations() { return maxRecommendations; }
                public void setMaxRecommendations(Integer maxRecommendations) { this.maxRecommendations = maxRecommendations; }
                public Double getDiversityFactor() { return diversityFactor; }
                public void setDiversityFactor(Double diversityFactor) { this.diversityFactor = diversityFactor; }
            }
            
            // Getters and setters
            public BehaviorTracking getBehaviorTracking() { return behaviorTracking; }
            public void setBehaviorTracking(BehaviorTracking behaviorTracking) { this.behaviorTracking = behaviorTracking; }
            public Preferences getPreferences() { return preferences; }
            public void setPreferences(Preferences preferences) { this.preferences = preferences; }
            public Recommendations getRecommendations() { return recommendations; }
            public void setRecommendations(Recommendations recommendations) { this.recommendations = recommendations; }
        }
        
        /**
         * Order-specific AI settings
         */
        public static class OrderSettings {
            private Analysis analysis = new Analysis();
            private Insights insights = new Insights();
            
            public static class Analysis {
                private Boolean enabled = true;
                private Boolean patternRecognition = true;
                private Boolean fraudDetection = true;
                
                // Getters and setters
                public Boolean getEnabled() { return enabled; }
                public void setEnabled(Boolean enabled) { this.enabled = enabled; }
                public Boolean getPatternRecognition() { return patternRecognition; }
                public void setPatternRecognition(Boolean patternRecognition) { this.patternRecognition = patternRecognition; }
                public Boolean getFraudDetection() { return fraudDetection; }
                public void setFraudDetection(Boolean fraudDetection) { this.fraudDetection = fraudDetection; }
            }
            
            public static class Insights {
                private Boolean enabled = true;
                private Boolean trendAnalysis = true;
                private Boolean seasonalPatterns = true;
                
                // Getters and setters
                public Boolean getEnabled() { return enabled; }
                public void setEnabled(Boolean enabled) { this.enabled = enabled; }
                public Boolean getTrendAnalysis() { return trendAnalysis; }
                public void setTrendAnalysis(Boolean trendAnalysis) { this.trendAnalysis = trendAnalysis; }
                public Boolean getSeasonalPatterns() { return seasonalPatterns; }
                public void setSeasonalPatterns(Boolean seasonalPatterns) { this.seasonalPatterns = seasonalPatterns; }
            }
            
            // Getters and setters
            public Analysis getAnalysis() { return analysis; }
            public void setAnalysis(Analysis analysis) { this.analysis = analysis; }
            public Insights getInsights() { return insights; }
            public void setInsights(Insights insights) { this.insights = insights; }
        }
        
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
        
        public Boolean getEnableProductDescriptionGeneration() { return enableProductDescriptionGeneration; }
        public void setEnableProductDescriptionGeneration(Boolean enableProductDescriptionGeneration) { this.enableProductDescriptionGeneration = enableProductDescriptionGeneration; }
        
        public Boolean getEnableProductCategorization() { return enableProductCategorization; }
        public void setEnableProductCategorization(Boolean enableProductCategorization) { this.enableProductCategorization = enableProductCategorization; }
        
        public Boolean getEnableProductTagging() { return enableProductTagging; }
        public void setEnableProductTagging(Boolean enableProductTagging) { this.enableProductTagging = enableProductTagging; }
        
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
        
        public ProductSettings getProduct() { return product; }
        public void setProduct(ProductSettings product) { this.product = product; }
        
        public UserSettings getUser() { return user; }
        public void setUser(UserSettings user) { this.user = user; }
        
        public OrderSettings getOrder() { return order; }
        public void setOrder(OrderSettings order) { this.order = order; }
    }
}