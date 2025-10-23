package com.ai.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for AI providers
 * 
 * This class contains all configuration properties for AI services including
 * OpenAI API settings, Pinecone vector database settings, and other AI provider configurations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.providers")
public class AIProviderConfig {
    
    // OpenAI Configuration
    private String openaiApiKey;
    private String openaiModel = "gpt-4o-mini";
    private String openaiEmbeddingModel = "text-embedding-3-small";
    private Integer openaiMaxTokens = 2000;
    private Double openaiTemperature = 0.3;
    private Integer openaiTimeout = 60;
    
    // Vector Database Configuration
    private String vectorDbType = "lucene"; // lucene, pinecone, memory
    private String vectorDbIndexPath = "./data/lucene-vector-index";
    private Double vectorDbSimilarityThreshold = 0.7;
    private Integer vectorDbMaxResults = 100;
    
    // Pinecone Configuration
    private String pineconeApiKey;
    private String pineconeEnvironment = "us-east-1-aws";
    private String pineconeIndexName = "ai-infrastructure";
    private Integer pineconeDimensions = 1536;
    
    // AI Features Configuration
    private Boolean enableRAG = true;
    private Boolean enableBehavioralAI = true;
    private Boolean enableSmartValidation = true;
    private Boolean enableAutoGeneration = true;
    
    // Performance Configuration
    private Integer maxConcurrentRequests = 10;
    private Integer cacheTimeoutMinutes = 60;
    private Boolean enableCaching = true;
    
    // Rate Limiting Configuration
    private Integer rateLimitPerMinute = 100;
    private Integer rateLimitBurstSize = 20;
    
    // Error Handling Configuration
    private Integer maxRetries = 3;
    private Integer retryDelayMs = 1000;
    private Boolean enableFallback = true;
}