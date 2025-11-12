package com.ai.infrastructure.relationship;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Entity Relationship Mapper
 * 
 * Maps entity type names (from LLM) to actual JPA entity class names.
 * Also provides relationship field name mappings.
 * 
 * Customers can extend this to provide their own mappings.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class EntityRelationshipMapper {
    
    // Map: entity type name -> entity class name
    private final Map<String, String> entityTypeToClass = new HashMap<>();
    
    // Map: (fromClass, toClass, relationshipType) -> fieldName
    private final Map<String, String> relationshipFieldMap = new HashMap<>();
    
    public EntityRelationshipMapper() {
        initializeDefaultMappings();
    }
    
    /**
     * Initialize default mappings
     * Customers should override this or provide their own mappings
     */
    private void initializeDefaultMappings() {
        // Common entity type mappings
        entityTypeToClass.put("document", "Document");
        entityTypeToClass.put("user", "User");
        entityTypeToClass.put("project", "Project");
        entityTypeToClass.put("order", "Order");
        entityTypeToClass.put("product", "Product");
        
        // Common relationship mappings
        // Format: "fromClass:toClass:relationshipType" -> "fieldName"
        relationshipFieldMap.put("Document:User:createdBy", "createdBy");
        relationshipFieldMap.put("Document:Project:belongsTo", "project");
        relationshipFieldMap.put("Project:User:owner", "owner");
        relationshipFieldMap.put("Order:User:customer", "customer");
        relationshipFieldMap.put("Order:Product:items", "items");
    }
    
    /**
     * Get entity class name for entity type
     * 
     * @param entityType entity type name (e.g., "document")
     * @return entity class name (e.g., "Document") or null if not found
     */
    public String getEntityClassName(String entityType) {
        if (entityType == null) {
            return null;
        }
        
        // Check cache
        String className = entityTypeToClass.get(entityType.toLowerCase());
        if (className != null) {
            return className;
        }
        
        // Try capitalized version
        String capitalized = capitalize(entityType);
        if (entityTypeToClass.containsKey(capitalized.toLowerCase())) {
            return capitalized;
        }
        
        // Return capitalized as default
        return capitalized;
    }
    
    /**
     * Get relationship field name
     * 
     * @param fromClass source entity class name
     * @param toClass target entity class name
     * @param relationshipType relationship type name
     * @return field name or null if not found
     */
    public String getRelationshipFieldName(String fromClass, String toClass, String relationshipType) {
        String key = fromClass + ":" + toClass + ":" + relationshipType;
        return relationshipFieldMap.get(key);
    }
    
    /**
     * Register entity type mapping
     * Customers can call this to register their entities
     */
    public void registerEntityType(String entityType, String className) {
        entityTypeToClass.put(entityType.toLowerCase(), className);
    }
    
    /**
     * Register relationship mapping
     * Customers can call this to register their relationships
     */
    public void registerRelationship(String fromClass, String toClass, 
                                     String relationshipType, String fieldName) {
        String key = fromClass + ":" + toClass + ":" + relationshipType;
        relationshipFieldMap.put(key, fieldName);
    }
    
    /**
     * Capitalize first letter
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
