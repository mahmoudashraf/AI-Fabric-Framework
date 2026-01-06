package com.ai.infrastructure.processor;

import com.ai.infrastructure.annotation.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Processor for @AICapable annotation
 *
 * <p>This processor handles entities annotated with @AICapable and provides
 * AI capabilities based on the annotation configuration. It integrates with
 * the new annotation system (@AISearchable, @AIContext) while maintaining
 * backward compatibility with legacy annotations (@AIEmbedding, @AIKnowledge).</p>
 *
 * <p><strong>Thread Safety:</strong> This is a singleton Spring bean. All methods are thread-safe.</p>
 *
 * <p><strong>Performance:</strong> Reflection results are cached via AnnotationFieldScanner
 * for optimal performance.</p>
 *
 * @author AI Infrastructure Team
 * @version 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AICapableProcessor {

    private final AnnotationFieldScanner fieldScanner;
    
    /**
     * Process an entity for AI capabilities
     * 
     * @param entity the entity to process
     * @return list of AI features enabled for this entity
     */
    public List<String> processEntity(Object entity) {
        List<String> features = new ArrayList<>();
        
        if (entity == null) {
            return features;
        }
        
        Class<?> entityClass = entity.getClass();
        
        // Check if class is annotated with @AICapable
        if (entityClass.isAnnotationPresent(AICapable.class)) {
            AICapable annotation = entityClass.getAnnotation(AICapable.class);
            features.addAll(extractFeatures(annotation));
            
            log.debug("Processed entity {} with AI features: {}", 
                entityClass.getSimpleName(), features);
        }
        
        // Check fields for AI annotations
        Field[] fields = entityClass.getDeclaredFields();
        for (Field field : fields) {
            if (field.isAnnotationPresent(AICapable.class)) {
                AICapable annotation = field.getAnnotation(AICapable.class);
                features.addAll(extractFeatures(annotation));
                
                log.debug("Processed field {} with AI features: {}", 
                    field.getName(), extractFeatures(annotation));
            }
        }
        
        return features;
    }
    
    /**
     * Extract AI features from annotation
     * 
     * @param annotation the @AICapable annotation
     * @return list of enabled features
     */
    private List<String> extractFeatures(AICapable annotation) {
        List<String> features = new ArrayList<>();
        
        if (annotation.autoEmbedding()) {
            features.add("EMBEDDING");
        }
        
        if (annotation.enableSearch()) {
            features.add("SEARCH");
        }
        
        if (annotation.enableRecommendations()) {
            features.add("RECOMMENDATIONS");
        }
        
        // Add features from the features array
        features.addAll(Arrays.asList(annotation.features()));
        
        return features;
    }
    
    /**
     * Check if an entity has specific AI capability
     * 
     * @param entity the entity to check
     * @param feature the feature to check for
     * @return true if the entity has the feature enabled
     */
    public boolean hasFeature(Object entity, String feature) {
        List<String> features = processEntity(entity);
        return features.contains(feature.toUpperCase());
    }
    
    /**
     * Get AI configuration for an entity
     * 
     * @param entity the entity to get configuration for
     * @return map of AI configuration
     */
    public java.util.Map<String, Object> getAIConfiguration(Object entity) {
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        
        if (entity == null) {
            return config;
        }
        
        Class<?> entityClass = entity.getClass();
        
        if (entityClass.isAnnotationPresent(AICapable.class)) {
            AICapable annotation = entityClass.getAnnotation(AICapable.class);
            config.put("autoEmbedding", annotation.autoEmbedding());
            config.put("enableSearch", annotation.enableSearch());
            config.put("enableRecommendations", annotation.enableRecommendations());
            config.put("entityType", annotation.entityType());
            config.put("autoProcess", annotation.autoProcess());
            config.put("indexable", annotation.indexable());
            config.put("features", Arrays.asList(annotation.features()));
            config.put("indexingStrategy", annotation.indexingStrategy());
            config.put("onCreateStrategy", annotation.onCreateStrategy());
            config.put("onUpdateStrategy", annotation.onUpdateStrategy());
            config.put("onDeleteStrategy", annotation.onDeleteStrategy());
        }
        
        return config;
    }
    
    /**
     * Get fields annotated with @AIEmbedding
     * 
     * @param entity the entity to process
     * @return list of embedding field information
     */
    public List<Map<String, Object>> getEmbeddingFields(Object entity) {
        if (entity == null) {
            return new ArrayList<>();
        }
        
        List<Map<String, Object>> embeddingFields = new ArrayList<>();
        Class<?> entityClass = entity.getClass();
        
        for (Field field : entityClass.getDeclaredFields()) {
            AIEmbedding annotation = field.getAnnotation(AIEmbedding.class);
            if (annotation != null) {
                Map<String, Object> fieldInfo = new HashMap<>();
                fieldInfo.put("fieldName", field.getName());
                fieldInfo.put("weight", annotation.weight());
                fieldInfo.put("required", annotation.required());
                fieldInfo.put("preprocess", annotation.preprocess());
                fieldInfo.put("includeInSimilarity", annotation.includeInSimilarity());
                fieldInfo.put("model", annotation.model());
                fieldInfo.put("autoGenerate", annotation.autoGenerate());
                fieldInfo.put("chunkingStrategy", annotation.chunkingStrategy());
                fieldInfo.put("maxChunkSize", annotation.maxChunkSize());
                
                // Get field value
                try {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    fieldInfo.put("value", value);
                } catch (IllegalAccessException e) {
                    log.warn("Could not access field {} for embedding", field.getName());
                }
                
                embeddingFields.add(fieldInfo);
            }
        }
        
        return embeddingFields;
    }
    
    /**
     * Get fields annotated with @AIKnowledge
     * 
     * @param entity the entity to process
     * @return list of knowledge field information
     */
    public List<Map<String, Object>> getKnowledgeFields(Object entity) {
        if (entity == null) {
            return new ArrayList<>();
        }
        
        List<Map<String, Object>> knowledgeFields = new ArrayList<>();
        Class<?> entityClass = entity.getClass();
        
        for (Field field : entityClass.getDeclaredFields()) {
            AIKnowledge annotation = field.getAnnotation(AIKnowledge.class);
            if (annotation != null) {
                Map<String, Object> fieldInfo = new HashMap<>();
                fieldInfo.put("fieldName", field.getName());
                fieldInfo.put("category", annotation.category());
                fieldInfo.put("importance", annotation.importance());
                fieldInfo.put("searchable", annotation.searchable());
                fieldInfo.put("includeInRAG", annotation.includeInRAG());
                fieldInfo.put("keywords", Arrays.asList(annotation.keywords()));
                fieldInfo.put("indexable", annotation.indexable());
                fieldInfo.put("type", annotation.type());
                fieldInfo.put("enableSemanticSearch", annotation.enableSemanticSearch());
                fieldInfo.put("enableKeywordSearch", annotation.enableKeywordSearch());
                
                // Get field value
                try {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    fieldInfo.put("value", value);
                } catch (IllegalAccessException e) {
                    log.warn("Could not access field {} for knowledge", field.getName());
                }
                
                knowledgeFields.add(fieldInfo);
            }
        }
        
        return knowledgeFields;
    }
    
    /**
     * Get fields annotated with @AISmartValidation
     * 
     * @param entity the entity to process
     * @return list of validation field information
     */
    public List<Map<String, Object>> getValidationFields(Object entity) {
        if (entity == null) {
            return new ArrayList<>();
        }
        
        List<Map<String, Object>> validationFields = new ArrayList<>();
        Class<?> entityClass = entity.getClass();
        
        for (Field field : entityClass.getDeclaredFields()) {
            AISmartValidation annotation = field.getAnnotation(AISmartValidation.class);
            if (annotation != null) {
                Map<String, Object> fieldInfo = new HashMap<>();
                fieldInfo.put("fieldName", field.getName());
                fieldInfo.put("rules", Arrays.asList(annotation.rules()));
                fieldInfo.put("validateContent", annotation.validateContent());
                fieldInfo.put("validateFormat", annotation.validateFormat());
                fieldInfo.put("validateSemantic", annotation.validateSemantic());
                fieldInfo.put("prompt", annotation.prompt());
                fieldInfo.put("required", annotation.required());
                fieldInfo.put("severity", annotation.severity().name());
                fieldInfo.put("realTime", annotation.realTime());
                fieldInfo.put("context", annotation.context());
                fieldInfo.put("crossField", annotation.crossField());
                
                // Get field value
                try {
                    field.setAccessible(true);
                    Object value = field.get(entity);
                    fieldInfo.put("value", value);
                } catch (IllegalAccessException e) {
                    log.warn("Could not access field {} for validation", field.getName());
                }
                
                validationFields.add(fieldInfo);
            }
        }
        
        return validationFields;
    }
    
    /**
     * Get all AI-annotated fields for an entity
     * 
     * @param entity the entity to process
     * @return map containing all AI field information
     */
    public Map<String, Object> getAllAIFields(Object entity) {
        Map<String, Object> allFields = new HashMap<>();
        allFields.put("embeddingFields", getEmbeddingFields(entity));
        allFields.put("knowledgeFields", getKnowledgeFields(entity));
        allFields.put("validationFields", getValidationFields(entity));
        return allFields;
    }
    
    /**
     * Check if an entity has any AI annotations
     *
     * @param entity the entity to check
     * @return true if entity has AI annotations
     */
    public boolean hasAIAnnotations(Object entity) {
        if (entity == null) {
            return false;
        }

        Class<?> entityClass = entity.getClass();

        // Check class-level annotation
        if (entityClass.isAnnotationPresent(AICapable.class)) {
            return true;
        }

        // Check field-level annotations (including new annotations)
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(AIEmbedding.class) ||
                field.isAnnotationPresent(AIKnowledge.class) ||
                field.isAnnotationPresent(AISmartValidation.class) ||
                field.isAnnotationPresent(AISearchable.class) ||
                field.isAnnotationPresent(AIContext.class)) {
                return true;
            }
        }

        return false;
    }

    // ========== New Annotation System (v2.0) ==========

    /**
     * Extracts searchable content from an entity using @AISearchable annotations.
     *
     * <p>This method delegates to {@link AnnotationFieldScanner} which caches
     * reflection results at application level for optimal performance.</p>
     *
     * <p><strong>New in v2.0:</strong> Replaces manual field extraction with
     * annotation-driven auto-discovery.</p>
     *
     * @param entity The entity instance to extract searchable content from
     * @return Combined searchable content (never null, may be empty)
     * @throws IllegalArgumentException if entity is null
     * @see AISearchable
     * @see AnnotationFieldScanner#extractSearchableContent(Object)
     */
    public String extractSearchableContent(Object entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        return fieldScanner.extractSearchableContent(entity);
    }

    /**
     * Extracts context metadata from an entity using @AIContext annotations.
     *
     * <p>This method delegates to {@link AnnotationFieldScanner} which caches
     * reflection results at application level for optimal performance.</p>
     *
     * <p><strong>New in v2.0:</strong> Structured metadata for LLM context without
     * the overhead of vector embeddings.</p>
     *
     * @param entity The entity instance to extract context metadata from
     * @return Metadata map (never null, may be empty)
     * @throws IllegalArgumentException if entity is null
     * @see AIContext
     * @see AnnotationFieldScanner#extractContextMetadata(Object)
     */
    public Map<String, Object> extractContextMetadata(Object entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }
        return fieldScanner.extractContextMetadata(entity);
    }

    /**
     * Gets metadata for all @AISearchable fields on an entity class.
     *
     * <p>Results are cached at application level for performance.</p>
     *
     * @param entityClass The entity class to scan
     * @return List of searchable field metadata (never null)
     * @throws IllegalArgumentException if entityClass is null
     * @see AISearchable
     * @see AnnotationFieldScanner#getSearchableFields(Class)
     */
    public List<AnnotationFieldScanner.SearchableFieldMetadata> getSearchableFieldMetadata(Class<?> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass cannot be null");
        }
        return fieldScanner.getSearchableFields(entityClass);
    }

    /**
     * Gets metadata for all @AIContext fields on an entity class.
     *
     * <p>Results are cached at application level for performance.
     * Fields are sorted by priority (highest first).</p>
     *
     * @param entityClass The entity class to scan
     * @return List of context field metadata (never null)
     * @throws IllegalArgumentException if entityClass is null
     * @see AIContext
     * @see AnnotationFieldScanner#getContextFields(Class)
     */
    public List<AnnotationFieldScanner.ContextFieldMetadata> getContextFieldMetadata(Class<?> entityClass) {
        if (entityClass == null) {
            throw new IllegalArgumentException("entityClass cannot be null");
        }
        return fieldScanner.getContextFields(entityClass);
    }

    /**
     * Checks if an entity has @AISearchable fields.
     *
     * @param entity The entity to check
     * @return true if entity has @AISearchable fields
     */
    public boolean hasSearchableFields(Object entity) {
        if (entity == null) {
            return false;
        }
        List<AnnotationFieldScanner.SearchableFieldMetadata> fields =
            fieldScanner.getSearchableFields(entity.getClass());
        return !fields.isEmpty();
    }

    /**
     * Checks if an entity has @AIContext fields.
     *
     * @param entity The entity to check
     * @return true if entity has @AIContext fields
     */
    public boolean hasContextFields(Object entity) {
        if (entity == null) {
            return false;
        }
        List<AnnotationFieldScanner.ContextFieldMetadata> fields =
            fieldScanner.getContextFields(entity.getClass());
        return !fields.isEmpty();
    }

    /**
     * Extracts complete AI data from an entity including both searchable content
     * and context metadata.
     *
     * <p>This is the primary method for preparing entity data for AI indexing.</p>
     *
     * @param entity The entity instance to process
     * @return Map containing searchableContent and contextMetadata
     * @throws IllegalArgumentException if entity is null
     */
    public Map<String, Object> extractCompleteAIData(Object entity) {
        if (entity == null) {
            throw new IllegalArgumentException("entity cannot be null");
        }

        Map<String, Object> aiData = new LinkedHashMap<>();
        aiData.put("searchableContent", extractSearchableContent(entity));
        aiData.put("contextMetadata", extractContextMetadata(entity));
        aiData.put("hasSearchableFields", hasSearchableFields(entity));
        aiData.put("hasContextFields", hasContextFields(entity));

        return aiData;
    }
}
