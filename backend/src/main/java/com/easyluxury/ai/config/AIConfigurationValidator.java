package com.easyluxury.ai.config;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.AIConfigurationService;
import com.easyluxury.ai.config.EasyLuxuryAIConfig.EasyLuxuryAISettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI Configuration Validator
 * 
 * This component validates AI configuration during application startup
 * and provides comprehensive health checks for the AI infrastructure.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(AIProviderConfig.class)
public class AIConfigurationValidator {
    
    private final Environment environment;
    private final AIProviderConfig aiProviderConfig;
    private final AIServiceConfig aiServiceConfig;
    private final AIConfigurationService aiConfigurationService;
    private final EasyLuxuryAISettings aiSettings;
    
    /**
     * Validate AI configuration on application startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateAIConfiguration() {
        log.info("Starting AI configuration validation...");
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        
        // Validate AI Provider Configuration
        validateAIProviderConfig(errors, warnings);
        
        // Validate AI Service Configuration
        validateAIServiceConfig(errors, warnings);
        
        // Validate Easy Luxury AI Settings
        validateEasyLuxuryAISettings(errors, warnings);
        
        // Validate Environment-specific settings
        validateEnvironmentSettings(errors, warnings);
        
        // Validate AI Infrastructure
        validateAIInfrastructure(errors, warnings);
        
        // Log results
        logValidationResults(errors, warnings);
        
        // Throw exception if critical errors found
        if (!errors.isEmpty()) {
            throw new IllegalStateException("AI configuration validation failed: " + String.join(", ", errors));
        }
    }
    
    /**
     * Validate AI Provider Configuration
     */
    private void validateAIProviderConfig(List<String> errors, List<String> warnings) {
        log.debug("Validating AI Provider Configuration...");
        
        try {
            // Check if provider config is available
            if (aiProviderConfig == null) {
                errors.add("AI Provider Configuration is not available");
                return;
            }
            
            // Validate OpenAI settings
            AIProviderConfig.OpenAIConfig openai = aiProviderConfig.getOpenai();
            if (openai.getApiKey() == null || openai.getApiKey().trim().isEmpty()) {
                if (isProductionEnvironment()) {
                    errors.add("OpenAI API key is required in production environment");
                } else {
                    warnings.add("OpenAI API key not configured (using mock responses)");
                }
            }
            
            if (openai.getModel() == null || openai.getModel().trim().isEmpty()) {
                errors.add("OpenAI model is not specified");
            }
            
            if (openai.getEmbeddingModel() == null || openai.getEmbeddingModel().trim().isEmpty()) {
                warnings.add("OpenAI embedding model not specified, using default");
            }
            
            // Validate Pinecone settings
            AIProviderConfig.PineconeConfig pinecone = aiProviderConfig.getPinecone();
            if (pinecone.getApiKey() == null || pinecone.getApiKey().trim().isEmpty()) {
                if (isProductionEnvironment()) {
                    errors.add("Pinecone API key is required in production environment");
                } else {
                    warnings.add("Pinecone API key not configured (using in-memory vector DB)");
                }
            }
            
            if (pinecone.getIndexName() == null || pinecone.getIndexName().trim().isEmpty()) {
                warnings.add("Pinecone index name not specified, using default");
            }
            
        } catch (Exception e) {
            errors.add("Failed to validate AI Provider Configuration: " + e.getMessage());
        }
    }
    
    /**
     * Validate AI Service Configuration
     */
    private void validateAIServiceConfig(List<String> errors, List<String> warnings) {
        log.debug("Validating AI Service Configuration...");
        
        try {
            if (aiServiceConfig == null) {
                errors.add("AI Service Configuration is not available");
                return;
            }
            
            // Validate service settings
            if (aiServiceConfig.getDefaultProvider() == null || aiServiceConfig.getDefaultProvider().trim().isEmpty()) {
                errors.add("Default AI provider is not specified");
            }
            
            if (aiServiceConfig.getDefaultTimeout() <= 0) {
                warnings.add("AI service timeout not properly configured, using default");
            }
            
            if (aiServiceConfig.getMaxRetries() < 0) {
                warnings.add("AI service max retries not properly configured, using default");
            }
            
            // Validate feature flags
            if (aiServiceConfig.getFeatureFlags() != null) {
                var flags = aiServiceConfig.getFeatureFlags();
                
                if (flags.get("embedding.enabled") == null) {
                    warnings.add("Embedding feature flag not explicitly set");
                }
                
                if (flags.get("search.enabled") == null) {
                    warnings.add("Search feature flag not explicitly set");
                }
                
                if (flags.get("rag.enabled") == null) {
                    warnings.add("RAG feature flag not explicitly set");
                }
            }
            
        } catch (Exception e) {
            errors.add("Failed to validate AI Service Configuration: " + e.getMessage());
        }
    }
    
    /**
     * Validate Easy Luxury AI Settings
     */
    private void validateEasyLuxuryAISettings(List<String> errors, List<String> warnings) {
        log.debug("Validating Easy Luxury AI Settings...");
        
        try {
            if (aiSettings == null) {
                errors.add("Easy Luxury AI Settings are not available");
                return;
            }
            
            // Validate index names
            if (aiSettings.getProductIndexName() == null || aiSettings.getProductIndexName().trim().isEmpty()) {
                errors.add("Product index name is required");
            }
            
            if (aiSettings.getUserIndexName() == null || aiSettings.getUserIndexName().trim().isEmpty()) {
                errors.add("User index name is required");
            }
            
            if (aiSettings.getOrderIndexName() == null || aiSettings.getOrderIndexName().trim().isEmpty()) {
                errors.add("Order index name is required");
            }
            
            // Validate AI model settings
            if (aiSettings.getMaxTokens() <= 0) {
                errors.add("Max tokens must be positive");
            }
            
            if (aiSettings.getTemperature() < 0 || aiSettings.getTemperature() > 2) {
                errors.add("Temperature must be between 0 and 2");
            }
            
            if (aiSettings.getTimeoutSeconds() <= 0) {
                errors.add("Timeout seconds must be positive");
            }
            
            // Validate feature toggles
            if (aiSettings.getEnableProductRecommendations() && aiSettings.getProductIndexName() == null) {
                warnings.add("Product recommendations enabled but product index not configured");
            }
            
            if (aiSettings.getEnableUserBehaviorTracking() && aiSettings.getUserIndexName() == null) {
                warnings.add("User behavior tracking enabled but user index not configured");
            }
            
        } catch (Exception e) {
            errors.add("Failed to validate Easy Luxury AI Settings: " + e.getMessage());
        }
    }
    
    /**
     * Validate Environment-specific settings
     */
    private void validateEnvironmentSettings(List<String> errors, List<String> warnings) {
        log.debug("Validating Environment-specific settings...");
        
        try {
            String activeProfile = getActiveProfile();
            
            if ("prod".equals(activeProfile) || "production".equals(activeProfile)) {
                // Production-specific validations
                validateProductionSettings(errors, warnings);
            } else if ("test".equals(activeProfile)) {
                // Test-specific validations
                validateTestSettings(errors, warnings);
            } else if ("dev".equals(activeProfile)) {
                // Development-specific validations
                validateDevelopmentSettings(errors, warnings);
            }
            
        } catch (Exception e) {
            errors.add("Failed to validate environment settings: " + e.getMessage());
        }
    }
    
    /**
     * Validate production settings
     */
    private void validateProductionSettings(List<String> errors, List<String> warnings) {
        log.debug("Validating production settings...");
        
        // Check for mock responses in production
        boolean mockResponses = environment.getProperty("ai.provider.openai.mock-responses", Boolean.class, false);
        if (mockResponses) {
            errors.add("Mock responses should not be enabled in production environment");
        }
        
        // Check for required API keys
        String openaiKey = environment.getProperty("ai.provider.openai.api-key");
        if (openaiKey == null || openaiKey.trim().isEmpty() || "sk-dev-key".equals(openaiKey)) {
            errors.add("Valid OpenAI API key is required in production");
        }
        
        // Check for proper logging levels
        String logLevel = environment.getProperty("logging.level.com.easyluxury");
        if ("DEBUG".equals(logLevel) || "TRACE".equals(logLevel)) {
            warnings.add("Debug logging enabled in production - consider using INFO level");
        }
        
        // Check for proper security settings
        boolean securityEnabled = environment.getProperty("ai.service.security.enabled", Boolean.class, true);
        if (!securityEnabled) {
            warnings.add("AI security features are disabled in production");
        }
    }
    
    /**
     * Validate test settings
     */
    private void validateTestSettings(List<String> errors, List<String> warnings) {
        log.debug("Validating test settings...");
        
        // Check for proper test database
        String dbUrl = environment.getProperty("spring.datasource.url");
        if (dbUrl == null || !dbUrl.contains("h2:mem")) {
            warnings.add("Test environment should use H2 in-memory database");
        }
        
        // Check for mock responses enabled
        boolean mockResponses = environment.getProperty("ai.provider.openai.mock-responses", Boolean.class, true);
        if (!mockResponses) {
            warnings.add("Mock responses should be enabled in test environment");
        }
    }
    
    /**
     * Validate development settings
     */
    private void validateDevelopmentSettings(List<String> errors, List<String> warnings) {
        log.debug("Validating development settings...");
        
        // Check for development-specific configurations
        String vectorDbType = environment.getProperty("ai.vector-db.type", "memory");
        if (!"memory".equals(vectorDbType) && !"lucene".equals(vectorDbType)) {
            warnings.add("Development environment should use memory or Lucene vector database");
        }
        
        // Check for proper timeout settings
        int timeout = environment.getProperty("ai.service.default-timeout", Integer.class, 10000);
        if (timeout > 30000) {
            warnings.add("Development timeout is set high - consider reducing for faster feedback");
        }
    }
    
    /**
     * Validate AI Infrastructure
     */
    private void validateAIInfrastructure(List<String> errors, List<String> warnings) {
        log.debug("Validating AI Infrastructure...");
        
        try {
            if (aiConfigurationService == null) {
                errors.add("AI Configuration Service is not available");
                return;
            }
            
            // Validate configuration service
            boolean configValid = aiConfigurationService.validateConfiguration();
            if (!configValid) {
                if (isProductionEnvironment()) {
                    errors.add("AI Configuration Service validation failed");
                } else {
                    warnings.add("AI Configuration Service validation failed (acceptable in test/dev environments)");
                }
            }
            
            // Check for required services
            // Note: AIConfigurationService doesn't have getConfiguration() method
            // This validation is handled by the validateConfiguration() method above
            
        } catch (Exception e) {
            errors.add("Failed to validate AI Infrastructure: " + e.getMessage());
        }
    }
    
    /**
     * Log validation results
     */
    private void logValidationResults(List<String> errors, List<String> warnings) {
        if (errors.isEmpty() && warnings.isEmpty()) {
            log.info("✅ AI configuration validation completed successfully - all checks passed");
        } else {
            if (!errors.isEmpty()) {
                log.error("❌ AI configuration validation failed with {} critical errors:", errors.size());
                errors.forEach(error -> log.error("  - {}", error));
            }
            
            if (!warnings.isEmpty()) {
                log.warn("⚠️  AI configuration validation completed with {} warnings:", warnings.size());
                warnings.forEach(warning -> log.warn("  - {}", warning));
            }
        }
    }
    
    /**
     * Check if running in production environment
     */
    private boolean isProductionEnvironment() {
        String activeProfile = getActiveProfile();
        return "prod".equals(activeProfile) || "production".equals(activeProfile);
    }
    
    /**
     * Get active Spring profile
     */
    private String getActiveProfile() {
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length > 0) {
            return profiles[0];
        }
        return environment.getProperty("spring.profiles.active", "default");
    }
}
