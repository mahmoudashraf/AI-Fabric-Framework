package com.ai.infrastructure.processor;

import com.ai.infrastructure.annotation.AICapable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Processor for @AICapable annotation
 * 
 * This processor handles entities annotated with @AICapable and provides
 * AI capabilities based on the annotation configuration.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Component
public class AICapableProcessor {
    
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
        
        if (annotation.enableRAG()) {
            features.add("RAG");
        }
        
        if (annotation.enableEmbedding()) {
            features.add("EMBEDDING");
        }
        
        if (annotation.enableSearch()) {
            features.add("SEARCH");
        }
        
        if (annotation.enableValidation()) {
            features.add("VALIDATION");
        }
        
        if (annotation.enableGeneration()) {
            features.add("GENERATION");
        }
        
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
            config.put("enableRAG", annotation.enableRAG());
            config.put("enableEmbedding", annotation.enableEmbedding());
            config.put("enableSearch", annotation.enableSearch());
            config.put("enableValidation", annotation.enableValidation());
            config.put("enableGeneration", annotation.enableGeneration());
            config.put("entityType", annotation.entityType());
            config.put("priority", annotation.priority());
        }
        
        return config;
    }
}
