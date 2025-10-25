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
 * Generic Behavior Repository
 * 
 * Repository for behavioral data operations.
 * This repository is domain-agnostic and can be used across different applications.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Repository
public interface BehaviorRepository extends JpaRepository<Behavior, UUID> {
    
    /**
     * Find behaviors by user ID
     */
    List<Behavior> findByUserId(UUID userId);
    
    /**
     * Find behaviors by user ID with pagination
     */
    Page<Behavior> findByUserId(UUID userId, Pageable pageable);
    
    /**
     * Find behaviors by behavior type
     */
    List<Behavior> findByBehaviorType(Behavior.BehaviorType behaviorType);
    
    /**
     * Find behaviors by behavior type with pagination
     */
    Page<Behavior> findByBehaviorType(Behavior.BehaviorType behaviorType, Pageable pageable);
    
    /**
     * Find behaviors by user ID and behavior type
     */
    List<Behavior> findByUserIdAndBehaviorType(UUID userId, Behavior.BehaviorType behaviorType);
    
    /**
     * Find behaviors by user ID and behavior type with pagination
     */
    Page<Behavior> findByUserIdAndBehaviorType(UUID userId, Behavior.BehaviorType behaviorType, Pageable pageable);
    
    /**
     * Find behaviors by entity type and entity ID
     */
    List<Behavior> findByEntityTypeAndEntityId(String entityType, String entityId);
    
    /**
     * Find behaviors by session ID
     */
    List<Behavior> findBySessionId(String sessionId);
    
    /**
     * Find behaviors by date range
     */
    List<Behavior> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find behaviors by user ID and date range
     */
    List<Behavior> findByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find behaviors by user ID and date range with pagination
     */
    Page<Behavior> findByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Count behaviors by user ID
     */
    long countByUserId(UUID userId);
    
    /**
     * Count behaviors by behavior type
     */
    long countByBehaviorType(Behavior.BehaviorType behaviorType);
    
    /**
     * Count behaviors by user ID and behavior type
     */
    long countByUserIdAndBehaviorType(UUID userId, Behavior.BehaviorType behaviorType);
    
    /**
     * Find behaviors with AI analysis
     */
    @Query("SELECT b FROM Behavior b WHERE b.aiAnalysis IS NOT NULL AND b.aiAnalysis != ''")
    List<Behavior> findBehaviorsWithAIAnalysis();
    
    /**
     * Find behaviors with AI insights
     */
    @Query("SELECT b FROM Behavior b WHERE b.aiInsights IS NOT NULL AND b.aiInsights != ''")
    List<Behavior> findBehaviorsWithAIInsights();
    
    /**
     * Find behaviors by pattern flags
     */
    @Query("SELECT b FROM Behavior b WHERE b.patternFlags IS NOT NULL AND b.patternFlags != ''")
    List<Behavior> findBehaviorsWithPatternFlags();
    
    /**
     * Find behaviors by behavior score range
     */
    @Query("SELECT b FROM Behavior b WHERE b.behaviorScore BETWEEN :minScore AND :maxScore")
    List<Behavior> findByBehaviorScoreBetween(@Param("minScore") Double minScore, @Param("maxScore") Double maxScore);
    
    /**
     * Find behaviors by significance score range
     */
    @Query("SELECT b FROM Behavior b WHERE b.significanceScore BETWEEN :minScore AND :maxScore")
    List<Behavior> findBySignificanceScoreBetween(@Param("minScore") Double minScore, @Param("maxScore") Double maxScore);
    
    /**
     * Find top behaviors by score
     */
    @Query("SELECT b FROM Behavior b WHERE b.behaviorScore IS NOT NULL ORDER BY b.behaviorScore DESC")
    List<Behavior> findTopBehaviorsByScore(Pageable pageable);
    
    /**
     * Find top behaviors by significance score
     */
    @Query("SELECT b FROM Behavior b WHERE b.significanceScore IS NOT NULL ORDER BY b.significanceScore DESC")
    List<Behavior> findTopBehaviorsBySignificanceScore(Pageable pageable);
}