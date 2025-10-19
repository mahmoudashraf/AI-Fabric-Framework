package com.ai.infrastructure.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AIEmbedding Annotation
 * 
 * Marks a field as containing or requiring AI embeddings for vector operations.
 * Used for semantic search, similarity matching, and RAG operations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AIEmbedding {
    
    /**
     * The type of embedding (e.g., "text", "image", "audio")
     */
    String type() default "text";
    
    /**
     * The AI model to use for generating embeddings
     */
    String model() default "";
    
    /**
     * The dimension of the embedding vector
     */
    int dimension() default 1536;
    
    /**
     * Whether to automatically generate embeddings for this field
     */
    boolean autoGenerate() default true;
    
    /**
     * Whether to normalize the embedding vector
     */
    boolean normalize() default true;
    
    /**
     * The similarity threshold for matching (0.0 to 1.0)
     */
    double similarityThreshold() default 0.7;
    
    /**
     * The similarity metric to use ("cosine", "euclidean", "dot_product")
     */
    String similarityMetric() default "cosine";
    
    /**
     * Whether to enable indexing for this embedding
     */
    boolean indexable() default true;
    
    /**
     * The index name for this embedding
     */
    String indexName() default "";
    
    /**
     * Custom parameters for embedding generation
     */
    String[] parameters() default {};
    
    /**
     * Whether to enable caching for this embedding
     */
    boolean cacheable() default true;
    
    /**
     * Cache TTL for this embedding (in seconds)
     */
    long cacheTtlSeconds() default 3600L;
    
    /**
     * Whether to enable compression for this embedding
     */
    boolean compressible() default false;
    
    /**
     * The compression algorithm to use
     */
    String compressionAlgorithm() default "gzip";
    
    /**
     * Whether to enable encryption for this embedding
     */
    boolean encryptable() default false;
    
    /**
     * The encryption algorithm to use
     */
    String encryptionAlgorithm() default "AES-256-GCM";
    
    /**
     * Whether to enable quantization for this embedding
     */
    boolean quantizable() default false;
    
    /**
     * The quantization method to use
     */
    String quantizationMethod() default "int8";
    
    /**
     * Whether to enable pruning for this embedding
     */
    boolean prunable() default false;
    
    /**
     * The pruning ratio (0.0 to 1.0)
     */
    double pruningRatio() default 0.1;
    
    /**
     * Whether to enable clustering for this embedding
     */
    boolean clusterable() default false;
    
    /**
     * The number of clusters for this embedding
     */
    int numClusters() default 100;
    
    /**
     * Whether to enable dimensionality reduction
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
     * Whether to enable feature selection
     */
    boolean selectable() default false;
    
    /**
     * The number of features to select
     */
    int numFeatures() default 100;
    
    /**
     * Whether to enable feature scaling
     */
    boolean scalable() default true;
    
    /**
     * The scaling method to use
     */
    String scalingMethod() default "standard";
    
    /**
     * Whether to enable feature engineering
     */
    boolean engineerable() default false;
    
    /**
     * The feature engineering methods to use
     */
    String[] engineeringMethods() default {};
    
    /**
     * Whether to enable feature validation
     */
    boolean validatable() default true;
    
    /**
     * The validation rules for this embedding
     */
    String[] validationRules() default {};
    
    /**
     * Whether to enable feature monitoring
     */
    boolean monitorable() default true;
    
    /**
     * The monitoring metrics for this embedding
     */
    String[] monitoringMetrics() default {};
    
    /**
     * Whether to enable feature alerting
     */
    boolean alertable() default false;
    
    /**
     * The alert thresholds for this embedding
     */
    String[] alertThresholds() default {};
    
    /**
     * Whether to enable feature reporting
     */
    boolean reportable() default true;
    
    /**
     * The reporting frequency for this embedding
     */
    String reportingFrequency() default "daily";
    
    /**
     * Whether to enable feature visualization
     */
    boolean visualizable() default false;
    
    /**
     * The visualization methods for this embedding
     */
    String[] visualizationMethods() default {};
    
    /**
     * Whether to enable feature exploration
     */
    boolean explorable() default false;
    
    /**
     * The exploration methods for this embedding
     */
    String[] explorationMethods() default {};
    
    /**
     * Whether to enable feature discovery
     */
    boolean discoverable() default false;
    
    /**
     * The discovery methods for this embedding
     */
    String[] discoveryMethods() default {};
    
    /**
     * Whether to enable feature profiling
     */
    boolean profilable() default false;
    
    /**
     * The profiling methods for this embedding
     */
    String[] profilingMethods() default {};
    
    /**
     * Whether to enable feature lineage
     */
    boolean lineageable() default false;
    
    /**
     * The lineage methods for this embedding
     */
    String[] lineageMethods() default {};
    
    /**
     * Whether to enable feature governance
     */
    boolean governable() default false;
    
    /**
     * The governance methods for this embedding
     */
    String[] governanceMethods() default {};
    
    /**
     * Whether to enable feature compliance
     */
    boolean compliant() default false;
    
    /**
     * The compliance standards for this embedding
     */
    String[] complianceStandards() default {};
    
    /**
     * Whether to enable feature security
     */
    boolean securable() default false;
    
    /**
     * The security methods for this embedding
     */
    String[] securityMethods() default {};
    
    /**
     * Whether to enable feature privacy
     */
    boolean privateable() default false;
    
    /**
     * The privacy methods for this embedding
     */
    String[] privacyMethods() default {};
    
    /**
     * Whether to enable feature retention
     */
    boolean retainable() default false;
    
    /**
     * The retention period for this embedding (in days)
     */
    int retentionPeriodDays() default 365;
    
    /**
     * Whether to enable feature lifecycle management
     */
    boolean lifecycleable() default false;
    
    /**
     * The lifecycle methods for this embedding
     */
    String[] lifecycleMethods() default {};
}