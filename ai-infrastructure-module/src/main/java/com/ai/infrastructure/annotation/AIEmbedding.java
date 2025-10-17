package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for embedding generation
 * 
 * This annotation indicates that a field should be processed
 * to generate vector embeddings for semantic search and similarity.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AIEmbedding {
    
    /**
     * Weight of this field in the overall embedding
     * 
     * @return weight value (0.0 to 1.0)
     */
    double weight() default 1.0;
    
    /**
     * Whether this field is required for embedding generation
     * 
     * @return true if field is required
     */
    boolean required() default true;
    
    /**
     * Custom preprocessing instructions
     * 
     * @return preprocessing instructions
     */
    String preprocess() default "";
    
    /**
     * Field name for embedding storage
     * 
     * @return field name
     */
    String fieldName() default "";
    
    /**
     * Whether to include this field in similarity calculations
     * 
     * @return true if field should be included in similarity
     */
    boolean includeInSimilarity() default true;
    
    /**
     * Embedding model to use for this field
     * 
     * @return embedding model name
     */
    String model() default "";
    
    /**
     * Whether to generate embeddings automatically
     * 
     * @return true if embeddings should be generated automatically
     */
    boolean autoGenerate() default true;
    
    /**
     * Chunking strategy for this field
     * 
     * @return chunking strategy
     */
    ChunkingStrategy chunkingStrategy() default ChunkingStrategy.SMART;
    
    /**
     * Maximum chunk size for this field
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
        SMART,
        NONE
    }
}
