package com.ai.infrastructure.relationship;

import com.ai.infrastructure.dto.RelationshipQueryPlan;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.SingularAttribute;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Dynamic JPA Query Builder
 * 
 * Translates LLM-extracted relationship query plans into actual JPA queries.
 * Uses JPA Metamodel to discover entity relationships dynamically.
 * 
 * Flow:
 * 1. LLM analyzes user query → generates RelationshipQueryPlan
 * 2. This service translates plan → generates JPQL queries
 * 3. Executes queries → returns results
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class DynamicJPAQueryBuilder {
    
    private final EntityManager entityManager;
    private final EntityRelationshipMapper entityMapper;
    
    // Cache of discovered relationships
    private final Map<String, EntityRelationshipInfo> relationshipCache = new HashMap<>();
    
    public DynamicJPAQueryBuilder(EntityManager entityManager,
                                  EntityRelationshipMapper entityMapper) {
        this.entityManager = entityManager;
        this.entityMapper = entityMapper;
    }
    
    /**
     * Build JPA query from relationship query plan
     * 
     * @param plan the LLM-generated relationship query plan
     * @return JPQL query string, or null if cannot be built
     */
    public String buildQuery(RelationshipQueryPlan plan) {
        try {
            log.debug("Building JPA query from plan: {}", plan.getStrategy());
            
            if (plan.getRelationshipPaths() == null || plan.getRelationshipPaths().isEmpty()) {
                // No relationships, simple query
                return buildSimpleQuery(plan);
            }
            
            // Build query with relationship joins
            return buildRelationshipQuery(plan);
            
        } catch (Exception e) {
            log.error("Error building JPA query from plan", e);
            return null;
        }
    }
    
    /**
     * Build simple query without relationships
     */
    private String buildSimpleQuery(RelationshipQueryPlan plan) {
        String entityType = plan.getPrimaryEntityType();
        String entityClassName = entityMapper.getEntityClassName(entityType);
        
        if (entityClassName == null) {
            log.warn("Unknown entity type: {}", entityType);
            return null;
        }
        
        StringBuilder jpql = new StringBuilder();
        jpql.append("SELECT e FROM ").append(entityClassName).append(" e ");
        jpql.append("WHERE 1=1 ");
        
        // Add direct filters
        if (plan.getDirectFilters() != null) {
            for (Map.Entry<String, Object> filter : plan.getDirectFilters().entrySet()) {
                jpql.append("AND e.").append(filter.getKey()).append(" = :").append(filter.getKey()).append(" ");
            }
        }
        
        return jpql.toString();
    }
    
    /**
     * Build query with relationship joins
     */
    private String buildRelationshipQuery(RelationshipQueryPlan plan) {
        String primaryEntityType = plan.getPrimaryEntityType();
        String primaryEntityClass = entityMapper.getEntityClassName(primaryEntityType);
        
        if (primaryEntityClass == null) {
            log.warn("Unknown primary entity type: {}", primaryEntityType);
            return null;
        }
        
        StringBuilder jpql = new StringBuilder();
        jpql.append("SELECT DISTINCT e FROM ").append(primaryEntityClass).append(" e ");
        
        // Build joins for each relationship path
        Map<String, String> aliasMap = new HashMap<>();
        aliasMap.put(primaryEntityType, "e");
        int aliasCounter = 1;
        
        for (RelationshipQueryPlan.RelationshipPath path : plan.getRelationshipPaths()) {
            String joinAlias = buildJoin(jpql, path, aliasMap, aliasCounter);
            if (joinAlias != null) {
                aliasCounter++;
            }
        }
        
        // Add WHERE conditions
        jpql.append("WHERE 1=1 ");
        
        // Add relationship filters
        if (plan.getRelationshipFilters() != null) {
            for (Map.Entry<String, Object> filter : plan.getRelationshipFilters().entrySet()) {
                addRelationshipFilter(jpql, filter, aliasMap);
            }
        }
        
        // Add path conditions
        for (RelationshipQueryPlan.RelationshipPath path : plan.getRelationshipPaths()) {
            if (path.getConditions() != null && !path.getConditions().isEmpty()) {
                addPathConditions(jpql, path, aliasMap);
            }
        }
        
        // Add direct filters
        if (plan.getDirectFilters() != null) {
            for (Map.Entry<String, Object> filter : plan.getDirectFilters().entrySet()) {
                String alias = aliasMap.get(primaryEntityType);
                jpql.append("AND ").append(alias).append(".").append(filter.getKey())
                    .append(" = :").append(sanitizeParamName(filter.getKey())).append(" ");
            }
        }
        
        return jpql.toString();
    }
    
    /**
     * Build JOIN clause for a relationship path
     */
    private String buildJoin(StringBuilder jpql,
                            RelationshipQueryPlan.RelationshipPath path,
                            Map<String, String> aliasMap,
                            int aliasCounter) {
        try {
            String fromType = path.getFromEntityType();
            String toType = path.getToEntityType();
            String relationshipType = path.getRelationshipType();
            
            // Get entity class names
            String fromClass = entityMapper.getEntityClassName(fromType);
            String toClass = entityMapper.getEntityClassName(toType);
            
            if (fromClass == null || toClass == null) {
                log.warn("Unknown entity types: {} or {}", fromType, toType);
                return null;
            }
            
            // Get relationship field name
            String relationshipFieldName = discoverRelationshipFieldName(
                fromClass, toClass, relationshipType
            );
            
            if (relationshipFieldName == null) {
                log.warn("Could not discover relationship field: {} -> {} ({})", 
                    fromType, toType, relationshipType);
                return null;
            }
            
            // Get source alias
            String sourceAlias = aliasMap.get(fromType);
            if (sourceAlias == null) {
                // Use primary entity alias
                sourceAlias = "e";
            }
            
            // Create target alias
            String targetAlias = "r" + aliasCounter;
            aliasMap.put(toType, targetAlias);
            
            // Determine join type based on direction
            RelationshipQueryPlan.RelationshipDirection direction = path.getDirection();
            
            if (direction == RelationshipQueryPlan.RelationshipDirection.FORWARD ||
                direction == RelationshipQueryPlan.RelationshipDirection.BIDIRECTIONAL) {
                // Forward: e.relationshipField -> target
                jpql.append("JOIN ").append(sourceAlias).append(".").append(relationshipFieldName)
                    .append(" ").append(targetAlias).append(" ");
            } else {
                // Reverse: need to find reverse relationship
                String reverseFieldName = discoverReverseRelationshipFieldName(
                    toClass, fromClass, relationshipType
                );
                if (reverseFieldName != null) {
                    jpql.append("JOIN ").append(targetAlias).append(".").append(reverseFieldName)
                        .append(" ").append(sourceAlias).append(" ");
                } else {
                    // Fallback: try forward join
                    jpql.append("JOIN ").append(sourceAlias).append(".").append(relationshipFieldName)
                        .append(" ").append(targetAlias).append(" ");
                }
            }
            
            return targetAlias;
            
        } catch (Exception e) {
            log.error("Error building join for path: {}", path, e);
            return null;
        }
    }
    
    /**
     * Discover relationship field name using JPA Metamodel
     */
    private String discoverRelationshipFieldName(String fromClass, String toClass, String relationshipType) {
        try {
            Metamodel metamodel = entityManager.getMetamodel();
            
            // Get entity type
            EntityType<?> entityType = metamodel.getEntityTypes().stream()
                .filter(et -> et.getJavaType().getSimpleName().equals(fromClass) ||
                            et.getJavaType().getName().equals(fromClass))
                .findFirst()
                .orElse(null);
            
            if (entityType == null) {
                return null;
            }
            
            // Try to find attribute matching relationship type
            // Common patterns: createdBy, belongsTo, owner, etc.
            Set<String> possibleNames = generatePossibleFieldNames(relationshipType);
            
            for (String fieldName : possibleNames) {
                try {
                    SingularAttribute<?, ?> attr = entityType.getSingularAttribute(fieldName);
                    if (attr != null) {
                        Class<?> targetType = attr.getJavaType();
                        String targetSimpleName = targetType.getSimpleName();
                        
                        // Check if target type matches
                        if (targetSimpleName.equals(toClass) || 
                            targetType.getName().equals(toClass)) {
                            return fieldName;
                        }
                    }
                } catch (IllegalArgumentException e) {
                    // Attribute doesn't exist, try next
                    continue;
                }
            }
            
            // Try entity mapper as fallback
            return entityMapper.getRelationshipFieldName(fromClass, toClass, relationshipType);
            
        } catch (Exception e) {
            log.debug("Error discovering relationship field name", e);
            return null;
        }
    }
    
    /**
     * Generate possible field names for a relationship type
     */
    private Set<String> generatePossibleFieldNames(String relationshipType) {
        Set<String> names = new LinkedHashSet<>();
        
        // Direct match
        names.add(relationshipType);
        
        // Common patterns
        if (relationshipType.contains("By")) {
            names.add(relationshipType.toLowerCase());
        }
        
        // Camel case variations
        names.add(toCamelCase(relationshipType));
        names.add(toCamelCase(relationshipType.toLowerCase()));
        
        // Common relationship names
        if (relationshipType.equalsIgnoreCase("createdBy")) {
            names.add("createdBy");
            names.add("creator");
            names.add("author");
        }
        if (relationshipType.equalsIgnoreCase("belongsTo")) {
            names.add("project");
            names.add("parent");
            names.add("container");
        }
        if (relationshipType.equalsIgnoreCase("owner")) {
            names.add("owner");
            names.add("ownedBy");
        }
        
        return names;
    }
    
    /**
     * Discover reverse relationship field name
     */
    private String discoverReverseRelationshipFieldName(String fromClass, String toClass, String relationshipType) {
        // For reverse relationships, look for @OneToMany or collection fields
        try {
            Metamodel metamodel = entityManager.getMetamodel();
            
            EntityType<?> entityType = metamodel.getEntityTypes().stream()
                .filter(et -> et.getJavaType().getSimpleName().equals(fromClass))
                .findFirst()
                .orElse(null);
            
            if (entityType == null) {
                return null;
            }
            
            // Look for collection attributes that might be the reverse
            // This is simplified - in production, check @OneToMany annotations
            
            // Common reverse names
            Set<String> possibleNames = new LinkedHashSet<>();
            possibleNames.add(toClass.toLowerCase() + "s"); // documents
            possibleNames.add(toClass.toLowerCase()); // document
            possibleNames.add("owned" + toClass); // ownedDocuments
            
            for (String fieldName : possibleNames) {
                try {
                    entityType.getAttribute(fieldName);
                    return fieldName;
                } catch (IllegalArgumentException e) {
                    continue;
                }
            }
            
            return null;
            
        } catch (Exception e) {
            log.debug("Error discovering reverse relationship", e);
            return null;
        }
    }
    
    /**
     * Add relationship filter to WHERE clause
     */
    private void addRelationshipFilter(StringBuilder jpql,
                                      Map.Entry<String, Object> filter,
                                      Map<String, String> aliasMap) {
        String filterKey = filter.getKey();
        
        // Handle nested keys like "user.status"
        if (filterKey.contains(".")) {
            String[] parts = filterKey.split("\\.", 2);
            String entityType = parts[0];
            String fieldName = parts[1];
            
            String alias = aliasMap.get(entityType);
            if (alias != null) {
                String paramName = sanitizeParamName(filterKey);
                jpql.append("AND ").append(alias).append(".").append(fieldName)
                    .append(" = :").append(paramName).append(" ");
            }
        } else {
            // Direct filter on primary entity
            String alias = aliasMap.values().iterator().next();
            String paramName = sanitizeParamName(filterKey);
            jpql.append("AND ").append(alias).append(".").append(filterKey)
                .append(" = :").append(paramName).append(" ");
        }
    }
    
    /**
     * Add path conditions to WHERE clause
     */
    private void addPathConditions(StringBuilder jpql,
                                  RelationshipQueryPlan.RelationshipPath path,
                                  Map<String, String> aliasMap) {
        String targetType = path.getToEntityType();
        String alias = aliasMap.get(targetType);
        
        if (alias == null || path.getConditions() == null) {
            return;
        }
        
        for (Map.Entry<String, Object> condition : path.getConditions().entrySet()) {
            String paramName = sanitizeParamName(condition.getKey());
            jpql.append("AND ").append(alias).append(".").append(condition.getKey())
                .append(" = :").append(paramName).append(" ");
        }
    }
    
    /**
     * Sanitize parameter name for JPQL
     */
    private String sanitizeParamName(String name) {
        return name.replace(".", "_").replace("-", "_");
    }
    
    /**
     * Convert to camel case
     */
    private String toCamelCase(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toLowerCase() + str.substring(1);
    }
}
