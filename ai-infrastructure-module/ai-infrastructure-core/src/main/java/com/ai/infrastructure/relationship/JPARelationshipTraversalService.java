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
 * JPA Relationship Traversal Service
 * 
 * Uses JPA repositories and entity relationships to traverse relationships.
 * More efficient than metadata-based approach as it leverages database joins.
 * 
 * This service requires entities to have proper JPA relationships defined
 * (e.g., @ManyToOne, @OneToMany annotations).
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@Service
public class JPARelationshipTraversalService {
    
    private final AISearchableEntityRepository searchableEntityRepository;
    private final EntityManager entityManager;
    private final RelationshipQueryBuilder queryBuilder;
    
    public JPARelationshipTraversalService(AISearchableEntityRepository searchableEntityRepository,
                                          EntityManager entityManager,
                                          RelationshipQueryBuilder queryBuilder) {
        this.searchableEntityRepository = searchableEntityRepository;
        this.entityManager = entityManager;
        this.queryBuilder = queryBuilder;
    }
    
    /**
     * Traverse relationships using JPA queries
     * 
     * This method attempts to use JPA entity relationships first.
     * Falls back to metadata-based approach if JPA relationships are not available.
     * 
     * @param plan the relationship query plan
     * @return list of entity IDs that match the relationship traversal
     */
    @Transactional(readOnly = true)
    public List<String> traverseRelationships(RelationshipQueryPlan plan) {
        try {
            log.debug("Traversing relationships using JPA for plan: {}", plan.getStrategy());
            
            if (plan.getRelationshipPaths() == null || plan.getRelationshipPaths().isEmpty()) {
                log.debug("No relationship paths to traverse");
                return Collections.emptyList();
            }
            
            List<String> resultEntityIds = new ArrayList<>();
            
            // Execute each relationship path using JPA
            for (RelationshipQueryPlan.RelationshipPath path : plan.getRelationshipPaths()) {
                List<String> pathResults = traversePathWithJPA(path, plan);
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
            log.error("Error traversing relationships with JPA", e);
            // Fallback to metadata-based approach
            return traverseRelationshipsWithMetadata(plan);
        }
    }
    
    /**
     * Traverse a single relationship path using JPA
     */
    private List<String> traversePathWithJPA(RelationshipQueryPlan.RelationshipPath path,
                                             RelationshipQueryPlan plan) {
        try {
            String fromType = path.getFromEntityType();
            String toType = path.getToEntityType();
            String relationshipType = path.getRelationshipType();
            
            log.debug("Traversing JPA path: {} --[{}]--> {}", fromType, relationshipType, toType);
            
            // Build JPA query based on relationship path
            String jpql = buildJPAQuery(path, plan);
            
            if (jpql == null || jpql.isEmpty()) {
                log.debug("No JPA query generated, falling back to metadata approach");
                return traversePathWithMetadata(path, plan);
            }
            
            // Execute JPA query
            Query query = entityManager.createQuery(jpql);
            
            // Set parameters from conditions
            if (path.getConditions() != null) {
                for (Map.Entry<String, Object> param : path.getConditions().entrySet()) {
                    query.setParameter(param.getKey(), param.getValue());
                }
            }
            
            // Set relationship filters as parameters
            if (plan.getRelationshipFilters() != null) {
                for (Map.Entry<String, Object> filter : plan.getRelationshipFilters().entrySet()) {
                    String paramName = filter.getKey().replace(".", "_");
                    query.setParameter(paramName, filter.getValue());
                }
            }
            
            @SuppressWarnings("unchecked")
            List<Object> results = query.getResultList();
            
            // Extract entity IDs from results
            return results.stream()
                .map(this::extractEntityId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
            
        } catch (Exception e) {
            log.error("Error traversing JPA path: {}", path, e);
            return traversePathWithMetadata(path, plan);
        }
    }
    
    /**
     * Build JPA query for relationship traversal
     * 
     * This method generates JPQL queries based on common relationship patterns.
     * Customers can extend RelationshipQueryBuilder to provide custom queries.
     */
    private String buildJPAQuery(RelationshipQueryPlan.RelationshipPath path,
                                RelationshipQueryPlan plan) {
        String fromType = path.getFromEntityType();
        String toType = path.getToEntityType();
        String relationshipType = path.getRelationshipType();
        RelationshipQueryPlan.RelationshipDirection direction = path.getDirection();
        
        // Common pattern: document --[createdBy]--> user
        if ("document".equalsIgnoreCase(fromType) && 
            "user".equalsIgnoreCase(toType) && 
            "createdBy".equalsIgnoreCase(relationshipType)) {
            
            // Query: Find documents where createdBy user has specific status
            StringBuilder jpql = new StringBuilder();
            jpql.append("SELECT d.id FROM Document d ");
            jpql.append("JOIN d.createdBy u ");
            jpql.append("WHERE 1=1 ");
            
            // Add conditions
            if (path.getConditions() != null && path.getConditions().containsKey("status")) {
                jpql.append("AND u.status = :status ");
            }
            
            // Add relationship filters
            if (plan.getRelationshipFilters() != null) {
                if (plan.getRelationshipFilters().containsKey("user.status")) {
                    jpql.append("AND u.status = :user_status ");
                }
            }
            
            return jpql.toString();
        }
        
        // Common pattern: document --[belongsTo]--> project
        if ("document".equalsIgnoreCase(fromType) && 
            "project".equalsIgnoreCase(toType) && 
            "belongsTo".equalsIgnoreCase(relationshipType)) {
            
            StringBuilder jpql = new StringBuilder();
            jpql.append("SELECT d.id FROM Document d ");
            jpql.append("JOIN d.project p ");
            jpql.append("WHERE 1=1 ");
            
            if (path.getConditions() != null) {
                if (path.getConditions().containsKey("category")) {
                    jpql.append("AND p.category = :category ");
                }
            }
            
            return jpql.toString();
        }
        
        // Multi-hop: document → project → user
        if ("document".equalsIgnoreCase(fromType) && 
            "user".equalsIgnoreCase(toType) &&
            plan.getRelationshipPaths().size() > 1) {
            
            StringBuilder jpql = new StringBuilder();
            jpql.append("SELECT d.id FROM Document d ");
            jpql.append("JOIN d.project p ");
            jpql.append("JOIN p.owner u ");
            jpql.append("WHERE 1=1 ");
            
            if (plan.getRelationshipFilters() != null) {
                if (plan.getRelationshipFilters().containsKey("user.status")) {
                    jpql.append("AND u.status = :user_status ");
                }
            }
            
            return jpql.toString();
        }
        
        // Try custom query builder
        String customQuery = queryBuilder.buildRelationshipQuery(path, plan);
        if (customQuery != null && !customQuery.isEmpty()) {
            return customQuery;
        }
        
        // No JPA query available, return null to fall back to metadata
        return null;
    }
    
    /**
     * Fallback: Traverse using metadata (when JPA relationships not available)
     */
    private List<String> traverseRelationshipsWithMetadata(RelationshipQueryPlan plan) {
        log.debug("Falling back to metadata-based traversal");
        
        List<String> resultEntityIds = new ArrayList<>();
        
        for (RelationshipQueryPlan.RelationshipPath path : plan.getRelationshipPaths()) {
            List<String> pathResults = traversePathWithMetadata(path, plan);
            resultEntityIds.addAll(pathResults);
        }
        
        return resultEntityIds.stream()
            .distinct()
            .collect(Collectors.toList());
    }
    
    /**
     * Traverse path using metadata (fallback method)
     */
    private List<String> traversePathWithMetadata(RelationshipQueryPlan.RelationshipPath path,
                                                 RelationshipQueryPlan plan) {
        try {
            String fromType = path.getFromEntityType();
            String relationshipType = path.getRelationshipType();
            
            // Get all entities of the source type
            List<AISearchableEntity> candidates = searchableEntityRepository.findByEntityType(fromType);
            
            if (candidates.isEmpty()) {
                return Collections.emptyList();
            }
            
            List<String> matchingIds = new ArrayList<>();
            
            for (AISearchableEntity entity : candidates) {
                if (matchesRelationshipMetadata(entity, relationshipType, path.getConditions())) {
                    matchingIds.add(entity.getEntityId());
                }
            }
            
            return matchingIds;
            
        } catch (Exception e) {
            log.error("Error traversing path with metadata", e);
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
            Map<String, Object> metadata = parseMetadata(entity.getMetadata());
            Object relationshipValue = metadata.get(relationshipType);
            
            if (relationshipValue == null) {
                return false;
            }
            
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
            
            for (Map.Entry<String, Object> filter : filters.entrySet()) {
                String filterKey = filter.getKey();
                Object filterValue = filter.getValue();
                
                if (filterKey.contains(".")) {
                    // Nested filter like "user.status"
                    String[] parts = filterKey.split("\\.", 2);
                    String relationshipType = parts[0];
                    String fieldName = parts[1];
                    
                    // For JPA approach, this would be handled by the query
                    // For metadata fallback, check if relationship exists
                    if (!metadata.containsKey(relationshipType)) {
                        return false;
                    }
                } else {
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
            // Simple JSON parsing - in production, use Jackson ObjectMapper
            Map<String, Object> result = new HashMap<>();
            String content = metadataJson.trim();
            
            if (content.startsWith("{") && content.endsWith("}")) {
                content = content.substring(1, content.length() - 1).trim();
            }
            
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
        
        if (result instanceof String) {
            return (String) result;
        }
        
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
