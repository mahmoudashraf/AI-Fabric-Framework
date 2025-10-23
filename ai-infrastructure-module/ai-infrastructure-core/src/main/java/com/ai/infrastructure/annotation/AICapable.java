package com.ai.infrastructure.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AICapable Annotation
 * 
 * Entity-level annotation to enable AI capabilities for classes.
 * AI behavior is defined in the configuration file.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AICapable {
    
    /**
     * Entity type for AI processing
     * Used to lookup configuration in ai-entity-config.yml
     */
    String entityType() default "";
    
    /**
     * Configuration file path
     * Default: ai-entity-config.yml
     */
    String configFile() default "ai-entity-config.yml";
    
    /**
     * Enable automatic AI processing
     * Default: true
     */
    boolean autoProcess() default true;
    
    /**
     * AI features to enable
     * Options: embedding, search, rag, recommendation, validation, analysis
     */
    String[] features() default {"embedding", "search"};
    
    /**
     * Enable search capabilities
     * Default: true
     */
    boolean enableSearch() default true;
    
    /**
     * Enable recommendation capabilities
     * Default: false
     */
    boolean enableRecommendations() default false;
    
    /**
     * Enable automatic embedding generation
     * Default: true
     */
    boolean autoEmbedding() default true;
    
    /**
     * Enable indexing for search
     * Default: true
     */
    boolean indexable() default true;
}