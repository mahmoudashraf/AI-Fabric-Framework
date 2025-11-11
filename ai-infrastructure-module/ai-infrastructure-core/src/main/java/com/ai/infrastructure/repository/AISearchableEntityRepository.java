package com.ai.infrastructure.repository;

import com.ai.infrastructure.entity.AISearchableEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AI Searchable Entities
 * 
 * Provides data access methods for AI searchable entities.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Repository
public interface AISearchableEntityRepository extends JpaRepository<AISearchableEntity, String> {
    
    /**
     * Find by entity type and entity ID
     */
    Optional<AISearchableEntity> findByEntityTypeAndEntityId(String entityType, String entityId);
    
    /**
     * Find all by entity type
     */
    List<AISearchableEntity> findByEntityType(String entityType);

    /**
     * Find all by entity type with pagination support
     */
    Page<AISearchableEntity> findByEntityType(String entityType, Pageable pageable);
    
    /**
     * Delete by entity type and entity ID
     */
    void deleteByEntityTypeAndEntityId(String entityType, String entityId);
    
    /**
     * Delete all by entity type
     */
    void deleteByEntityType(String entityType);
    
    /**
     * Find by searchable content containing (case insensitive)
     */
    List<AISearchableEntity> findBySearchableContentContainingIgnoreCase(String content);
    
    /**
     * Find by vector ID
     */
    Optional<AISearchableEntity> findByVectorId(String vectorId);
    
    /**
     * Find all entities with vector IDs (not null)
     */
    List<AISearchableEntity> findByVectorIdIsNotNull();

    /**
     * Returns the most recently updated indexed entity, if any.
     */
    Optional<AISearchableEntity> findFirstByVectorUpdatedAtIsNotNullOrderByVectorUpdatedAtDesc();

    /**
     * Counts all entities that have an associated vector.
     */
    long countByVectorIdIsNotNull();
    
    /**
     * Find all entities without vector IDs (null)
     */
    List<AISearchableEntity> findByVectorIdIsNull();
    
    /**
     * Find entities by entity type that have vector IDs
     */
    List<AISearchableEntity> findByEntityTypeAndVectorIdIsNotNull(String entityType);
    
    /**
     * Find entities by entity type that don't have vector IDs
     */
    List<AISearchableEntity> findByEntityTypeAndVectorIdIsNull(String entityType);
    
    /**
     * Count entities by entity type that have vector IDs
     */
    long countByEntityTypeAndVectorIdIsNotNull(String entityType);
    
    /**
     * Count entities by entity type that don't have vector IDs
     */
    long countByEntityTypeAndVectorIdIsNull(String entityType);
    
    /**
     * Delete by vector ID
     */
    void deleteByVectorId(String vectorId);
    
    /**
     * Delete all entities without vector IDs
     */
    void deleteByVectorIdIsNull();
    
    /**
     * Delete all entities by entity type that don't have vector IDs
     */
    void deleteByEntityTypeAndVectorIdIsNull(String entityType);

    /**
     * Find entities whose metadata contains the supplied snippet (case sensitive).
     */
    @Query("SELECT e FROM AISearchableEntity e WHERE e.metadata IS NOT NULL AND e.metadata LIKE %:snippet%")
    List<AISearchableEntity> findByMetadataContainingSnippet(@Param("snippet") String snippet);
}
