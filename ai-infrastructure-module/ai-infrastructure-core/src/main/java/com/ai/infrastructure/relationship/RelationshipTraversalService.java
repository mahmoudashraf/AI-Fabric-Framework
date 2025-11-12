package com.ai.infrastructure.relationship;

import com.ai.infrastructure.dto.RelationshipQueryPlan;
import com.ai.infrastructure.entity.AISearchableEntity;
import com.ai.infrastructure.repository.AISearchableEntityRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Relationship Traversal Service
 * 
 * Executes relational database queries based on relationship paths.
 * Traverses relationships between entities using JPA/Hibernate.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class RelationshipTraversalService {
    
    private final AISearchableEntityRepository searchableEntityRepository;
    private final EntityManager entityManager;
    private final RelationshipQueryBuilder queryBuilder;
    
    public RelationshipTraversalService(AISearchableEntityRepository searchableEntityRepository,
                                       EntityManager entityManager,
                                       RelationshipQueryBuilder queryBuilder) {
        this.searchableEntityRepository = searchableEntityRepository;
        this.entityManager = entityManager;
        this.queryBuilder = queryBuilder;
    }
    
    /**
     * Traverse relationships and find related entities
     * 
     * @param plan the relationship query plan
     * @return list of entity IDs that match the relationship traversal
     */
    @Transactional(readOnly = true)
    public List<String> traverseRelationships(RelationshipQueryPlan plan) {
        try {
            log.debug("Traversing relationships for plan: {}", plan.getStrategy());
            
            if (plan.getRelationshipPaths() == null || plan.getRelationshipPaths().isEmpty()) {
                log.debug("No relationship paths to traverse");
                return Collections.emptyList();
            }
            
            List<String> resultEntityIds = new ArrayList<>();
            
            // Execute each relationship path
            for (RelationshipQueryPlan.RelationshipPath path : plan.getRelationshipPaths()) {
                List<String> pathResults = traversePath(path, plan);
                resultEntityIds.addAll(pathResults);
            }
            
            // Apply relationship filters
            if (plan.getRelationshipFilters() != null && !plan.getRelationshipFilters().isEmpty()) {
                resultEntityIds = applyRelationshipFilters(resultEntityIds, plan);
            }
            
            // Remove duplicates while preserving order
            return resultEntityIds.stream()
                .distinct()
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Error traversing relationships", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Traverse a single relationship path
     */
    private List<String> traversePath(RelationshipQueryPlan.RelationshipPath path, 
                                       RelationshipQueryPlan plan) {
        try {
            String fromType = path.getFromEntityType();
            String toType = path.getToEntityType();
            String relationshipType = path.getRelationshipType();
            
            log.debug("Traversing path: {} --[{}]--> {}", fromType, relationshipType, toType);
            
            // Strategy 1: Use metadata-based filtering (works with current design)
            if (plan.getPrimaryEntityType() != null && plan.getPrimaryEntityType().equals(toType)) {
                return findEntitiesByRelationshipMetadata(fromType, toType, relationshipType, path.getConditions());
            }
            
            // Strategy 2: Use JPA queries if entities are JPA entities
            // This requires customer to provide relationship mappings
            return findEntitiesByJPAQuery(path, plan);
            
        } catch (Exception e) {
            log.error("Error traversing path: {}", path, e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Find entities using metadata filtering (current design approach)
     */
    private List<String> findEntitiesByRelationshipMetadata(String fromType, String toType, 
                                                           String relationshipType,
                                                           Map<String, Object> conditions) {
        try {
            // Find all entities of the target type
            List<AISearchableEntity> candidates = searchableEntityRepository.findByEntityType(toType);
            
            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }
            
            // Filter by relationship metadata
            // Example: If looking for documents created by user-123,
            // metadata should contain {"createdBy": "user-123"}
            List<String> matchingIds = new ArrayList<>();
            
            for (AISearchableEntity entity : candidates) {
                if (matchesRelationshipMetadata(entity, relationshipType, conditions)) {
                    matchingIds.add(entity.getEntityId());
                }
            }
            
            return matchingIds;
            
        } catch (Exception e) {
            log.error("Error finding entities by relationship metadata", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Check if entity matches relationship metadata
     */
    private boolean matchesRelationshipMetadata(AISearchableEntity entity, 
                                               String relationshipType,
                                               Map<String, Object> conditions) {
        if (entity.getMetadata() == null || entity.getMetadata().isEmpty()) {
            return false;
        }
        
        try {
            // Parse metadata JSON
            Map<String, Object> metadata = parseMetadata(entity.getMetadata());
            
            // Check if relationship field exists
            Object relationshipValue = metadata.get(relationshipType);
            if (relationshipValue == null) {
                return false;
            }
            
            // Apply conditions if any
            if (conditions != null && !conditions.isEmpty()) {
                for (Map.Entry<String, Object> condition : conditions.entrySet()) {
                    Object metadataValue = metadata.get(condition.getKey());
                    if (!Objects.equals(metadataValue, condition.getValue())) {
                        return false;
                    }
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.debug("Error parsing metadata for entity: {}", entity.getId(), e);
            return false;
        }
    }
    
    /**
     * Find entities using JPA queries (for customer-provided entity relationships)
     */
    private List<String> findEntitiesByJPAQuery(RelationshipQueryPlan.RelationshipPath path,
                                                 RelationshipQueryPlan plan) {
        try {
            // Build dynamic JPA query based on relationship path
            String jpql = queryBuilder.buildRelationshipQuery(path, plan);
            
            if (jpql == null || jpql.isEmpty()) {
                log.debug("No JPA query generated, falling back to metadata approach");
                return findEntitiesByRelationshipMetadata(
                    path.getFromEntityType(),
                    path.getToEntityType(),
                    path.getRelationshipType(),
                    path.getConditions()
                );
            }
            
            Query query = entityManager.createQuery(jpql);
            
            // Set parameters from conditions
            if (path.getConditions() != null) {
                for (Map.Entry<String, Object> param : path.getConditions().entrySet()) {
                    query.setParameter(param.getKey(), param.getValue());
                }
            }
            
            @SuppressWarnings("unchecked")
            List<Object> results = query.getResultList();
            
            // Extract entity IDs from results
            return results.stream()
                .map(result -> extractEntityId(result))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Error executing JPA relationship query", e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Apply relationship filters to entity IDs
     */
    private List<String> applyRelationshipFilters(List<String> entityIds, RelationshipQueryPlan plan) {
        if (entityIds.isEmpty() || plan.getRelationshipFilters() == null) {
            return entityIds;
        }
        
        List<String> filteredIds = new ArrayList<>();
        
        for (String entityId : entityIds) {
            if (matchesRelationshipFilters(entityId, plan.getPrimaryEntityType(), plan.getRelationshipFilters())) {
                filteredIds.add(entityId);
            }
        }
        
        return filteredIds;
    }
    
    /**
     * Check if entity matches relationship filters
     */
    private boolean matchesRelationshipFilters(String entityId, String entityType, 
                                              Map<String, Object> filters) {
        try {
            Optional<AISearchableEntity> entityOpt = 
                searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId);
            
            if (entityOpt.isEmpty()) {
                return false;
            }
            
            AISearchableEntity entity = entityOpt.get();
            Map<String, Object> metadata = parseMetadata(entity.getMetadata());
            
            // Check each filter
            for (Map.Entry<String, Object> filter : filters.entrySet()) {
                String filterKey = filter.getKey();
                Object filterValue = filter.getValue();
                
                // Handle nested keys like "user.status"
                if (filterKey.contains(".")) {
                    String[] parts = filterKey.split("\\.", 2);
                    String relationshipType = parts[0];
                    String fieldName = parts[1];
                    
                    // Get related entity ID from metadata
                    Object relatedEntityId = metadata.get(relationshipType);
                    if (relatedEntityId == null) {
                        return false;
                    }
                    
                    // Find related entity and check field
                    // This is simplified - in production, you'd query the actual entity
                    // For now, we check if the relationship exists
                    if (!metadata.containsKey(relationshipType)) {
                        return false;
                    }
                } else {
                    // Direct metadata filter
                    Object metadataValue = metadata.get(filterKey);
                    if (!Objects.equals(metadataValue, filterValue)) {
                        return false;
                    }
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.debug("Error matching relationship filters for entity: {}", entityId, e);
            return false;
        }
    }
    
    /**
     * Parse metadata JSON string
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMetadata(String metadataJson) {
        if (metadataJson == null || metadataJson.trim().isEmpty() || "{}".equals(metadataJson.trim())) {
            return Collections.emptyMap();
        }
        
        try {
            // Simple JSON parsing - in production, use proper JSON library
            // This is a simplified parser for basic JSON objects
            Map<String, Object> result = new HashMap<>();
            
            // Remove outer braces
            String content = metadataJson.trim();
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1).trim();
            }
            
            // Split by comma (simple approach)
            String[] pairs = content.split(",");
            for (String pair : pairs) {
                String[] keyValue = pair.split(":", 2);
                if (keyValue.length == 2) {
                    String key = keyValue[0].trim().replaceAll("\"", "");
                    String value = keyValue[1].trim().replaceAll("\"", "");
                    result.put(key, value);
                }
            }
            
            return result;
            
        } catch (Exception e) {
            log.debug("Error parsing metadata JSON: {}", metadataJson, e);
            return Collections.emptyMap();
        }
    }
    
    /**
     * Extract entity ID from query result
     */
    private String extractEntityId(Object result) {
        if (result == null) {
            return null;
        }
        
        // If result is already a string (entity ID)
        if (result instanceof String) {
            return (String) result;
        }
        
        // If result is an entity object, try to get ID field
        try {
            java.lang.reflect.Method getIdMethod = result.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(result);
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            log.debug("Could not extract ID from result: {}", result.getClass().getName());
            return result.toString();
        }
    }
}
