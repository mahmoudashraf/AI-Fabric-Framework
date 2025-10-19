package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AIKnowledge Annotation
 * 
 * Marks a field as containing knowledge that can be used for RAG operations,
 * content generation, and AI-powered analysis. Used for knowledge base integration.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AIKnowledge {
    
    /**
     * The type of knowledge (e.g., "text", "structured", "unstructured")
     */
    String type() default "text";
    
    /**
     * The category of knowledge (e.g., "product", "user", "order")
     */
    String category() default "";
    
    /**
     * The source of the knowledge
     */
    String source() default "";
    
    /**
     * The confidence level of the knowledge (0.0 to 1.0)
     */
    double confidence() default 1.0;
    
    /**
     * The priority of the knowledge (1-10, higher = more priority)
     */
    int priority() default 5;
    
    /**
     * Whether this knowledge is indexable for search
     */
    boolean indexable() default true;
    
    /**
     * Whether this knowledge is searchable
     */
    boolean searchable() default true;
    
    /**
     * Whether this knowledge is retrievable for RAG
     */
    boolean retrievable() default true;
    
    /**
     * Whether this knowledge is generatable for content creation
     */
    boolean generatable() default false;
    
    /**
     * Whether this knowledge is analyzable
     */
    boolean analyzable() default true;
    
    /**
     * Whether this knowledge is validatable
     */
    boolean validatable() default true;
    
    /**
     * Whether this knowledge is transformable
     */
    boolean transformable() default false;
    
    /**
     * Whether this knowledge is enrichable
     */
    boolean enrichable() default false;
    
    /**
     * Whether this knowledge is cacheable
     */
    boolean cacheable() default true;
    
    /**
     * Cache TTL for this knowledge (in seconds)
     */
    long cacheTtlSeconds() default 3600L;
    
    /**
     * Whether this knowledge is compressible
     */
    boolean compressible() default false;
    
    /**
     * The compression algorithm to use
     */
    String compressionAlgorithm() default "gzip";
    
    /**
     * Whether this knowledge is encryptable
     */
    boolean encryptable() default false;
    
    /**
     * The encryption algorithm to use
     */
    String encryptionAlgorithm() default "AES-256-GCM";
    
    /**
     * Whether this knowledge is quantizable
     */
    boolean quantizable() default false;
    
    /**
     * The quantization method to use
     */
    String quantizationMethod() default "int8";
    
    /**
     * Whether this knowledge is prunable
     */
    boolean prunable() default false;
    
    /**
     * The pruning ratio (0.0 to 1.0)
     */
    double pruningRatio() default 0.1;
    
    /**
     * Whether this knowledge is clusterable
     */
    boolean clusterable() default false;
    
    /**
     * The number of clusters for this knowledge
     */
    int numClusters() default 100;
    
    /**
     * Whether this knowledge is reducible
     */
    boolean reducible() default false;
    
    /**
     * The target dimension for reduction
     */
    int targetDimension() default 512;
    
    /**
     * The reduction method to use
     */
    String reductionMethod() default "PCA";
    
    /**
     * Whether this knowledge is selectable
     */
    boolean selectable() default false;
    
    /**
     * The number of features to select
     */
    int numFeatures() default 100;
    
    /**
     * Whether this knowledge is scalable
     */
    boolean scalable() default true;
    
    /**
     * The scaling method to use
     */
    String scalingMethod() default "standard";
    
    /**
     * Whether this knowledge is engineerable
     */
    boolean engineerable() default false;
    
    /**
     * The feature engineering methods to use
     */
    String[] engineeringMethods() default {};
    
    /**
     * Whether this knowledge is validatable
     */
    /**
     * The validation rules for this knowledge
     */
    String[] validationRules() default {};
    
    /**
     * Whether this knowledge is monitorable
     */
    boolean monitorable() default true;
    
    /**
     * The monitoring metrics for this knowledge
     */
    String[] monitoringMetrics() default {};
    
    /**
     * Whether this knowledge is alertable
     */
    boolean alertable() default false;
    
    /**
     * The alert thresholds for this knowledge
     */
    String[] alertThresholds() default {};
    
    /**
     * Whether this knowledge is reportable
     */
    boolean reportable() default true;
    
    /**
     * The reporting frequency for this knowledge
     */
    String reportingFrequency() default "daily";
    
    /**
     * Whether this knowledge is visualizable
     */
    boolean visualizable() default false;
    
    /**
     * The visualization methods for this knowledge
     */
    String[] visualizationMethods() default {};
    
    /**
     * Whether this knowledge is explorable
     */
    boolean explorable() default false;
    
    /**
     * The exploration methods for this knowledge
     */
    String[] explorationMethods() default {};
    
    /**
     * Whether this knowledge is discoverable
     */
    boolean discoverable() default false;
    
    /**
     * The discovery methods for this knowledge
     */
    String[] discoveryMethods() default {};
    
    /**
     * Whether this knowledge is profilable
     */
    boolean profilable() default false;
    
    /**
     * The profiling methods for this knowledge
     */
    String[] profilingMethods() default {};
    
    /**
     * Whether this knowledge is lineageable
     */
    boolean lineageable() default false;
    
    /**
     * The lineage methods for this knowledge
     */
    String[] lineageMethods() default {};
    
    /**
     * Whether this knowledge is governable
     */
    boolean governable() default false;
    
    /**
     * The governance methods for this knowledge
     */
    String[] governanceMethods() default {};
    
    /**
     * Whether this knowledge is compliant
     */
    boolean compliant() default false;
    
    /**
     * The compliance standards for this knowledge
     */
    String[] complianceStandards() default {};
    
    /**
     * Whether this knowledge is securable
     */
    boolean securable() default false;
    
    /**
     * The security methods for this knowledge
     */
    String[] securityMethods() default {};
    
    /**
     * Whether this knowledge is privateable
     */
    boolean privateable() default false;
    
    /**
     * The privacy methods for this knowledge
     */
    String[] privacyMethods() default {};
    
    /**
     * Whether this knowledge is retainable
     */
    boolean retainable() default false;
    
    /**
     * The retention period for this knowledge (in days)
     */
    int retentionPeriodDays() default 365;
    
    /**
     * Whether this knowledge is lifecycleable
     */
    boolean lifecycleable() default false;
    
    /**
     * The lifecycle methods for this knowledge
     */
    String[] lifecycleMethods() default {};
    
    /**
     * Custom parameters for this knowledge
     */
    String[] parameters() default {};
    
    /**
     * The tags for this knowledge
     */
    String[] tags() default {};
    
    /**
     * The metadata for this knowledge
     */
    String[] metadata() default {};
    
    /**
     * The processing pipeline for this knowledge
     */
    String[] processingPipeline() default {};
    
    /**
     * The quality checks for this knowledge
     */
    String[] qualityChecks() default {};
    
    /**
     * The privacy controls for this knowledge
     */
    String[] privacyControls() default {};
    
    /**
     * The feature flags for this knowledge
     */
    String[] featureFlags() default {};
    
    /**
     * The synchronization settings for this knowledge
     */
    String[] synchronizationSettings() default {};
    
    /**
     * The archiving settings for this knowledge
     */
    String[] archivingSettings() default {};
    
    /**
     * The backup settings for this knowledge
     */
    String[] backupSettings() default {};
    
    /**
     * The processing rules for this knowledge
     */
    String[] processingRules() default {};
    
    /**
     * The migration settings for this knowledge
     */
    String[] migrationSettings() default {};
    
    /**
     * The replication settings for this knowledge
     */
    String[] replicationSettings() default {};
    
    /**
     * The compression settings for this knowledge
     */
    String[] compressionSettings() default {};
    
    /**
     * The encryption settings for this knowledge
     */
    String[] encryptionSettings() default {};
    
    /**
     * The deduplication settings for this knowledge
     */
    String[] deduplicationSettings() default {};
    
    /**
     * The optimization settings for this knowledge
     */
    String[] optimizationSettings() default {};
    
    /**
     * The monitoring settings for this knowledge
     */
    String[] monitoringSettings() default {};
    
    /**
     * The alerting settings for this knowledge
     */
    String[] alertingSettings() default {};
    
    /**
     * The reporting settings for this knowledge
     */
    String[] reportingSettings() default {};
    
    /**
     * The visualization settings for this knowledge
     */
    String[] visualizationSettings() default {};
    
    /**
     * The exploration settings for this knowledge
     */
    String[] explorationSettings() default {};
    
    /**
     * The discovery settings for this knowledge
     */
    String[] discoverySettings() default {};
    
    /**
     * The profiling settings for this knowledge
     */
    String[] profilingSettings() default {};
    
    /**
     * The lineage settings for this knowledge
     */
    String[] lineageSettings() default {};
    
    /**
     * The governance settings for this knowledge
     */
    String[] governanceSettings() default {};
    
    /**
     * The compliance settings for this knowledge
     */
    String[] complianceSettings() default {};
    
    /**
     * The security settings for this knowledge
     */
    String[] securitySettings() default {};
    
    /**
     * The privacy settings for this knowledge
     */
    String[] privacySettings() default {};
    
    /**
     * The retention settings for this knowledge
     */
    String[] retentionSettings() default {};
    
    /**
     * The lifecycle settings for this knowledge
     */
    String[] lifecycleSettings() default {};
    
    /**
     * Importance level of this knowledge
     */
    int importance() default 1;
    
    /**
     * Whether to include in RAG
     */
    boolean includeInRAG() default true;
    
    /**
     * Keywords for this knowledge
     */
    String[] keywords() default {};
    
    /**
     * Whether to enable semantic search
     */
    boolean enableSemanticSearch() default true;
    
    /**
     * Whether to enable keyword search
     */
    boolean enableKeywordSearch() default true;
    
    /**
     * Field name for this knowledge
     */
    String fieldName() default "";
}