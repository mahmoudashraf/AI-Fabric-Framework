package com.ai.infrastructure.repository;

import com.ai.infrastructure.entity.Behavior;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Generic AI Profile Repository
 * 
 * Repository for AI profile operations.
 * This repository is domain-agnostic and can be used across different applications.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Repository
public interface BehaviorRepository extends JpaRepository<Behavior, UUID> {
    
    /**
     * Find AI profile by user ID
     */
    Behavior findByUserId(UUID userId);
    
    /**
     * Find behaviors by user ID
     */
    List<Behavior> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    /**
     * Find behaviors by user ID with pagination
     */
    Page<Behavior> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
    
    /**
     * Find AI profiles by status
     */
    List<Behavior> findByBehaviorType(Behavior.BehaviorType behaviorType);
    
    /**
     * Find AI profiles by status with pagination
     */
    Page<Behavior> findByBehaviorType(Behavior.BehaviorType behaviorType, Pageable pageable);
    
    /**
     * Find AI profiles by user ID and status
     */
    List<Behavior> findByUserIdAndBehaviorType(UUID userId, Behavior.BehaviorType behaviorType);
    
    
    
    /**
     * Find AI profiles by date range
     */
    List<Behavior> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find AI profiles by user ID and date range
     */
    List<Behavior> findByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find AI profiles by user ID and date range with pagination
     */
    Page<Behavior> findByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    
    /**
     * Find latest behaviors by user ID
     */
    @Query("SELECT b FROM Behavior b WHERE b.userId = :userId ORDER BY b.createdAt DESC")
    List<Behavior> findLatestBehaviorsByUserId(@Param("userId") UUID userId);
    
    /**
     * Find latest behavior by user ID
     */
    @Query("SELECT b FROM Behavior b WHERE b.userId = :userId ORDER BY b.createdAt DESC")
    Behavior findLatestBehaviorByUserId(@Param("userId") UUID userId);
    
    /**
     * Count AI profiles by user ID
     */
    long countByUserId(UUID userId);
    
    /**
     * Count AI profiles by status
     */
    long countByBehaviorType(Behavior.BehaviorType behaviorType);
    
    /**
     * Count AI profiles by user ID and status
     */
    long countByUserIdAndBehaviorType(UUID userId, Behavior.BehaviorType behaviorType);
    
    /**
     * Find behaviors by entity type and entity ID
     */
    List<Behavior> findByEntityTypeAndEntityId(String entityType, String entityId);
    
    /**
     * Find behaviors by session ID
     */
    List<Behavior> findBySessionId(String sessionId);
}