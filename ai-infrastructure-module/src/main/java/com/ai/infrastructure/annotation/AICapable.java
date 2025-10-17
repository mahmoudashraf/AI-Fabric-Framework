package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an entity as AI-capable, enabling automatic AI features
 * 
 * This annotation indicates that an entity should have AI capabilities
 * such as embedding generation, semantic search, and intelligent validation.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AICapable {
    
    /**
     * AI features to enable for this entity
     * 
     * @return array of enabled AI features
     */
    String[] features() default {
        "embedding",
        "search",
        "validation",
        "recommendation"
    };
    
    /**
     * Priority level for AI processing
     * 
     * @return priority level (1-10, higher is more important)
     */
    int priority() default 5;
    
    /**
     * Whether to enable automatic embedding generation
     * 
     * @return true if automatic embedding generation is enabled
     */
    boolean autoEmbedding() default true;
    
    /**
     * Whether to enable semantic search
     * 
     * @return true if semantic search is enabled
     */
    boolean enableSearch() default true;
    
    /**
     * Whether to enable AI validation
     * 
     * @return true if AI validation is enabled
     */
    boolean enableValidation() default true;
    
    /**
     * Whether to enable AI recommendations
     * 
     * @return true if AI recommendations are enabled
     */
    boolean enableRecommendations() default true;
    
    /**
     * Custom configuration for this entity
     * 
     * @return custom configuration map
     */
    String[] config() default {};
    
    /**
     * Entity type for categorization
     * 
     * @return entity type
     */
    String entityType() default "";
    
    /**
     * Whether to index this entity in the vector database
     * 
     * @return true if entity should be indexed
     */
    boolean indexable() default true;
    
    /**
     * Chunking strategy for large content
     * 
     * @return chunking strategy
     */
    ChunkingStrategy chunkingStrategy() default ChunkingStrategy.SENTENCE;
    
    /**
     * Maximum chunk size for content processing
     * 
     * @return maximum chunk size
     */
    int maxChunkSize() default 1000;
    
    /**
     * Chunking strategies
     */
    enum ChunkingStrategy {
        SENTENCE,
        WORD,
        CHARACTER,
        PARAGRAPH,
        SMART
    }
}
