package com.ai.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Vector Database Configuration Properties
 * 
 * Configuration properties for vector database selection and settings.
 * Supports different backends based on profiles and environment variables.
 * 
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.vector-db")
public class VectorDatabaseConfig {
    
    /**
     * Vector database type: memory, pinecone, weaviate, qdrant
     */
    private String type = "memory";
    
    /**
     * Default similarity threshold for searches
     */
    private double defaultThreshold = 0.7;
    
    /**
     * Default maximum results for searches
     */
    private int defaultLimit = 10;
    
    /**
     * Enable performance metrics collection
     */
    private boolean metricsEnabled = true;
    
    /**
     * In-memory database configuration
     */
    private InMemoryConfig memory = new InMemoryConfig();
    
    /**
     * Pinecone configuration
     */
    private PineconeConfig pinecone = new PineconeConfig();
    
    /**
     * Weaviate configuration
     */
    private WeaviateConfig weaviate = new WeaviateConfig();
    
    /**
     * Qdrant configuration
     */
    private QdrantConfig qdrant = new QdrantConfig();
    
    @Data
    public static class InMemoryConfig {
        /**
         * Maximum number of vectors to store in memory
         */
        private int maxVectors = 10000;
        
        /**
         * Enable periodic cleanup of old vectors
         */
        private boolean enableCleanup = false;
        
        /**
         * Cleanup interval in minutes
         */
        private int cleanupIntervalMinutes = 60;
        
        /**
         * Maximum age of vectors in minutes before cleanup
         */
        private int maxVectorAgeMinutes = 1440; // 24 hours
    }
    
    @Data
    public static class PineconeConfig {
        /**
         * Pinecone API key
         */
        private String apiKey;
        
        /**
         * Pinecone environment (e.g., "us-east-1-aws")
         */
        private String environment;
        
        /**
         * Pinecone index name
         */
        private String indexName;
        
        /**
         * Vector dimensions (typically 1536 for OpenAI)
         */
        private int dimensions = 1536;
        
        /**
         * Distance metric: cosine, euclidean, dotproduct
         */
        private String metric = "cosine";
        
        /**
         * Number of pods for the index
         */
        private int pods = 1;
        
        /**
         * Pod type (e.g., "p1", "s1")
         */
        private String podType = "p1";
    }
    
    @Data
    public static class WeaviateConfig {
        /**
         * Weaviate endpoint URL
         */
        private String endpoint;
        
        /**
         * Weaviate API key (if authentication is enabled)
         */
        private String apiKey;
        
        /**
         * Weaviate class name for vectors
         */
        private String className = "AIVector";
        
        /**
         * Connection timeout in milliseconds
         */
        private int timeoutMs = 30000;
        
        /**
         * Enable batch operations
         */
        private boolean batchEnabled = true;
        
        /**
         * Batch size for bulk operations
         */
        private int batchSize = 100;
    }
    
    @Data
    public static class QdrantConfig {
        /**
         * Qdrant endpoint URL
         */
        private String endpoint;
        
        /**
         * Qdrant API key (if authentication is enabled)
         */
        private String apiKey;
        
        /**
         * Qdrant collection name
         */
        private String collectionName = "ai_vectors";
        
        /**
         * Vector dimensions
         */
        private int dimensions = 1536;
        
        /**
         * Distance metric: Cosine, Euclidean, Dot
         */
        private String distance = "Cosine";
        
        /**
         * Connection timeout in milliseconds
         */
        private int timeoutMs = 30000;
    }
}