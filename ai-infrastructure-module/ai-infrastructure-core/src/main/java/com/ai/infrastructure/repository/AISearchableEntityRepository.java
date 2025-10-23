package com.ai.infrastructure.repository;

import com.ai.infrastructure.entity.AISearchableEntity;
import org.springframework.data.jpa.repository.JpaRepository;
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
}
