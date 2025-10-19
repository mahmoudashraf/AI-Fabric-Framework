package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AICapable Annotation
 * 
 * Marks an entity as AI-capable, enabling AI-powered features like search,
 * recommendations, and content generation. This annotation provides configuration
 * options for AI behavior and capabilities.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AICapable {
    
    /**
     * The type of entity for AI processing
     * Used for categorization and specialized AI handling
     */
    String entityType() default "";
    
    /**
     * Whether to enable AI-powered search for this entity
     */
    boolean enableSearch() default true;
    
    /**
     * Whether to enable AI-powered recommendations for this entity
     */
    boolean enableRecommendations() default true;
    
    /**
     * Whether to enable automatic embedding generation
     */
    boolean autoEmbedding() default true;
    
    /**
     * Whether this entity should be indexed for AI operations
     */
    boolean indexable() default true;
    
    /**
     * Priority level for AI processing (1-10, higher = more priority)
     */
    int priority() default 5;
    
    /**
     * AI model to use for this entity type
     */
    String model() default "";
    
    /**
     * Custom AI configuration parameters
     */
    String[] parameters() default {};
    
    /**
     * Whether to enable content generation for this entity
     */
    boolean enableContentGeneration() default false;
    
    /**
     * Whether to enable smart validation for this entity
     */
    boolean enableSmartValidation() default false;
    
    /**
     * Whether to enable behavioral analysis for this entity
     */
    boolean enableBehavioralAnalysis() default false;
    
    /**
     * Whether to enable RAG (Retrieval-Augmented Generation) for this entity
     */
    boolean enableRAG() default true;
    
    /**
     * Custom search fields for this entity
     */
    String[] searchFields() default {};
    
    /**
     * Custom recommendation fields for this entity
     */
    String[] recommendationFields() default {};
    
    /**
     * Whether to enable real-time AI processing
     */
    boolean enableRealTimeProcessing() default false;
    
    /**
     * Cache TTL for AI operations on this entity (in seconds)
     */
    long cacheTtlSeconds() default 3600L;
    
    /**
     * Whether to enable AI analytics for this entity
     */
    boolean enableAnalytics() default true;
    
    /**
     * Custom metadata for AI processing
     */
    String[] metadata() default {};
    
    /**
     * Whether to enable AI-powered data validation
     */
    boolean enableDataValidation() default false;
    
    /**
     * Whether to enable AI-powered data transformation
     */
    boolean enableDataTransformation() default false;
    
    /**
     * Whether to enable AI-powered data enrichment
     */
    boolean enableDataEnrichment() default false;
    
    /**
     * Custom AI processing pipeline for this entity
     */
    String[] processingPipeline() default {};
    
    /**
     * Whether to enable AI-powered data quality checks
     */
    boolean enableDataQualityChecks() default false;
    
    /**
     * Whether to enable AI-powered data privacy controls
     */
    boolean enablePrivacyControls() default false;
    
    /**
     * Custom AI feature flags for this entity
     */
    String[] featureFlags() default {};
    
    /**
     * Whether to enable AI-powered data synchronization
     */
    boolean enableDataSynchronization() default false;
    
    /**
     * Whether to enable AI-powered data archiving
     */
    boolean enableDataArchiving() default false;
    
    /**
     * Whether to enable AI-powered data backup
     */
    boolean enableDataBackup() default false;
    
    /**
     * Custom AI processing rules for this entity
     */
    String[] processingRules() default {};
    
    /**
     * Whether to enable AI-powered data migration
     */
    boolean enableDataMigration() default false;
    
    /**
     * Whether to enable AI-powered data replication
     */
    boolean enableDataReplication() default false;
    
    /**
     * Whether to enable AI-powered data compression
     */
    boolean enableDataCompression() default false;
    
    /**
     * Whether to enable AI-powered data encryption
     */
    boolean enableDataEncryption() default false;
    
    /**
     * Whether to enable AI-powered data deduplication
     */
    boolean enableDataDeduplication() default false;
    
    /**
     * Whether to enable AI-powered data compression
     */
    boolean enableDataOptimization() default false;
    
    /**
     * Whether to enable AI-powered data monitoring
     */
    boolean enableDataMonitoring() default true;
    
    /**
     * Whether to enable AI-powered data alerting
     */
    boolean enableDataAlerting() default true;
    
    /**
     * Whether to enable AI-powered data reporting
     */
    boolean enableDataReporting() default true;
    
    /**
     * Whether to enable AI-powered data visualization
     */
    boolean enableDataVisualization() default false;
    
    /**
     * Whether to enable AI-powered data exploration
     */
    boolean enableDataExploration() default false;
    
    /**
     * Whether to enable AI-powered data discovery
     */
    boolean enableDataDiscovery() default false;
    
    /**
     * Whether to enable AI-powered data profiling
     */
    boolean enableDataProfiling() default false;
    
    /**
     * Whether to enable AI-powered data lineage
     */
    boolean enableDataLineage() default false;
    
    /**
     * Whether to enable AI-powered data governance
     */
    boolean enableDataGovernance() default false;
    
    /**
     * Whether to enable AI-powered data compliance
     */
    boolean enableDataCompliance() default false;
    
    /**
     * Whether to enable AI-powered data security
     */
    boolean enableDataSecurity() default false;
    
    /**
     * Whether to enable AI-powered data privacy
     */
    boolean enableDataPrivacy() default false;
    
    /**
     * Whether to enable AI-powered data retention
     */
    boolean enableDataRetention() default false;
    
    /**
     * Whether to enable AI-powered data lifecycle management
     */
    boolean enableDataLifecycleManagement() default false;
    
    /**
     * Whether to enable validation
     */
    boolean enableValidation() default true;
}