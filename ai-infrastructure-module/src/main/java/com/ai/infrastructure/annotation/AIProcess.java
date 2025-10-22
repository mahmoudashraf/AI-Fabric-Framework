package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;

/**
 * AIProcess Annotation
 * 
 * Marks methods for automatic AI processing.
 * Used in combination with @AICapable for method-level AI processing.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AIProcess {
    
    /**
     * AI processing type
     * Options: create, update, delete, search, analyze
     */
    String processType() default "create";
    
    /**
     * Whether to generate embeddings for this method
     */
    boolean generateEmbedding() default true;
    
    /**
     * Whether to index for search for this method
     */
    boolean indexForSearch() default true;
    
    /**
     * Whether to enable analysis for this method
     */
    boolean enableAnalysis() default false;
    
    /**
     * Custom AI configuration parameters
     */
    String[] parameters() default {};
    
    /**
     * Priority level for AI processing (1-10, higher = more priority)
     */
    int priority() default 5;
    
    /**
     * Whether to enable real-time AI processing
     */
    boolean enableRealTimeProcessing() default false;
    
    /**
     * Cache TTL for AI operations (in seconds)
     */
    long cacheTtlSeconds() default 3600L;
    
    /**
     * Custom AI processing rules
     */
    String[] processingRules() default {};
    
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
     * Custom AI processing pipeline
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
     * Custom AI feature flags
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
     * Whether to enable AI-powered data optimization
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
}