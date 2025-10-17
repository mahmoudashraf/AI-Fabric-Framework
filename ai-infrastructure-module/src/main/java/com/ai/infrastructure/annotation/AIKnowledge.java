package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a field for knowledge base inclusion
 * 
 * This annotation indicates that a field should be included
 * in the knowledge base for RAG operations and context building.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AIKnowledge {
    
    /**
     * Knowledge category for this field
     * 
     * @return knowledge category
     */
    String category() default "general";
    
    /**
     * Importance level for this knowledge
     * 
     * @return importance level (1-10, higher is more important)
     */
    int importance() default 5;
    
    /**
     * Whether this field is searchable
     * 
     * @return true if field is searchable
     */
    boolean searchable() default true;
    
    /**
     * Whether this field should be included in RAG context
     * 
     * @return true if field should be included in RAG context
     */
    boolean includeInRAG() default true;
    
    /**
     * Custom search keywords for this field
     * 
     * @return search keywords
     */
    String[] keywords() default {};
    
    /**
     * Field name for knowledge storage
     * 
     * @return field name
     */
    String fieldName() default "";
    
    /**
     * Whether to index this field in the vector database
     * 
     * @return true if field should be indexed
     */
    boolean indexable() default true;
    
    /**
     * Knowledge type for categorization
     * 
     * @return knowledge type
     */
    KnowledgeType type() default KnowledgeType.TEXT;
    
    /**
     * Whether to enable semantic search for this field
     * 
     * @return true if semantic search is enabled
     */
    boolean enableSemanticSearch() default true;
    
    /**
     * Whether to enable keyword search for this field
     * 
     * @return true if keyword search is enabled
     */
    boolean enableKeywordSearch() default true;
    
    /**
     * Knowledge types
     */
    enum KnowledgeType {
        TEXT,
        STRUCTURED,
        METADATA,
        REFERENCE,
        CONTEXT
    }
}
