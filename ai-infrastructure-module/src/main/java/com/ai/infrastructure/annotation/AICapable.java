package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark entities as AI-capable, enabling automatic AI features
 * 
 * When applied to an entity, this annotation enables:
 * - RAG (Retrieval-Augmented Generation) capabilities
 * - Semantic search functionality
 * - AI-powered recommendations
 * - Smart validation
 * - Auto-generated AI APIs
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AICapable {
    
    /**
     * Enable RAG (Retrieval-Augmented Generation) capabilities
     * 
     * @return true if RAG is enabled
     */
    boolean enableRAG() default true;
    
    /**
     * Enable embedding generation
     * 
     * @return true if embedding is enabled
     */
    boolean enableEmbedding() default true;
    
    /**
     * Enable semantic search
     * 
     * @return true if search is enabled
     */
    boolean enableSearch() default true;
    
    /**
     * Enable smart validation
     * 
     * @return true if validation is enabled
     */
    boolean enableValidation() default true;
    
    /**
     * Enable content generation
     * 
     * @return true if generation is enabled
     */
    boolean enableGeneration() default true;
    
    /**
     * Entity type for AI context
     * 
     * @return entity type
     */
    String entityType() default "";
    
    /**
     * Priority for AI processing
     * 
     * @return priority level
     */
    int priority() default 0;
    
    /**
     * Description of the entity for AI context
     * 
     * @return entity description
     */
    String description() default "";
}