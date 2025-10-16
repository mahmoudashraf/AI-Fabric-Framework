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
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AICapable {
    
    /**
     * AI features to enable for this entity
     * 
     * @return array of enabled AI features
     */
    String[] features() default {"rag", "search", "recommendations", "validation"};
    
    /**
     * Description of the entity for AI context
     * 
     * @return entity description
     */
    String description() default "";
    
    /**
     * Whether to enable automatic indexing
     * 
     * @return true if auto-indexing is enabled
     */
    boolean autoIndex() default true;
    
    /**
     * Custom search fields for this entity
     * 
     * @return array of field names to include in search
     */
    String[] searchFields() default {};
    
    /**
     * Custom recommendation fields for this entity
     * 
     * @return array of field names to use for recommendations
     */
    String[] recommendationFields() default {};
}