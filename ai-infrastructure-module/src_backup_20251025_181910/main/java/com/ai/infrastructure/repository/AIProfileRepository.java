package com.ai.infrastructure.repository;

import com.ai.infrastructure.entity.AIProfile;
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
public interface AIProfileRepository extends JpaRepository<AIProfile, UUID> {
    
    /**
     * Find AI profile by user ID
     */
    AIProfile findByUserId(UUID userId);
    
    /**
     * Find AI profiles by status
     */
    List<AIProfile> findByStatus(AIProfile.AIProfileStatus status);
    
    /**
     * Find AI profiles by status with pagination
     */
    Page<AIProfile> findByStatus(AIProfile.AIProfileStatus status, Pageable pageable);
    
    /**
     * Find AI profiles by user ID and status
     */
    List<AIProfile> findByUserIdAndStatus(UUID userId, AIProfile.AIProfileStatus status);
    
    /**
     * Find AI profiles by confidence score range
     */
    @Query("SELECT p FROM AIProfile p WHERE p.confidenceScore BETWEEN :minScore AND :maxScore")
    List<AIProfile> findByConfidenceScoreBetween(@Param("minScore") Double minScore, @Param("maxScore") Double maxScore);
    
    /**
     * Find AI profiles by version
     */
    List<AIProfile> findByVersion(Integer version);
    
    /**
     * Find AI profiles by user ID and version
     */
    List<AIProfile> findByUserIdAndVersion(UUID userId, Integer version);
    
    /**
     * Find AI profiles by date range
     */
    List<AIProfile> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find AI profiles by user ID and date range
     */
    List<AIProfile> findByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime startDate, LocalDateTime endDate);
    
    /**
     * Find AI profiles by user ID and date range with pagination
     */
    Page<AIProfile> findByUserIdAndCreatedAtBetween(UUID userId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable);
    
    /**
     * Find AI profiles with preferences
     */
    @Query("SELECT p FROM AIProfile p WHERE p.preferences IS NOT NULL AND p.preferences != ''")
    List<AIProfile> findProfilesWithPreferences();
    
    /**
     * Find AI profiles with interests
     */
    @Query("SELECT p FROM AIProfile p WHERE p.interests IS NOT NULL AND p.interests != ''")
    List<AIProfile> findProfilesWithInterests();
    
    /**
     * Find AI profiles with behavior patterns
     */
    @Query("SELECT p FROM AIProfile p WHERE p.behaviorPatterns IS NOT NULL AND p.behaviorPatterns != ''")
    List<AIProfile> findProfilesWithBehaviorPatterns();
    
    /**
     * Find AI profiles with CV file
     */
    @Query("SELECT p FROM AIProfile p WHERE p.cvFileUrl IS NOT NULL AND p.cvFileUrl != ''")
    List<AIProfile> findProfilesWithCVFile();
    
    /**
     * Find top AI profiles by confidence score
     */
    @Query("SELECT p FROM AIProfile p WHERE p.confidenceScore IS NOT NULL ORDER BY p.confidenceScore DESC")
    List<AIProfile> findTopProfilesByConfidenceScore(Pageable pageable);
    
    /**
     * Find latest AI profiles by user ID
     */
    @Query("SELECT p FROM AIProfile p WHERE p.userId = :userId ORDER BY p.updatedAt DESC")
    List<AIProfile> findLatestProfilesByUserId(@Param("userId") UUID userId);
    
    /**
     * Find latest AI profile by user ID
     */
    @Query("SELECT p FROM AIProfile p WHERE p.userId = :userId ORDER BY p.updatedAt DESC")
    AIProfile findLatestProfileByUserId(@Param("userId") UUID userId);
    
    /**
     * Count AI profiles by user ID
     */
    long countByUserId(UUID userId);
    
    /**
     * Count AI profiles by status
     */
    long countByStatus(AIProfile.AIProfileStatus status);
    
    /**
     * Count AI profiles by user ID and status
     */
    long countByUserIdAndStatus(UUID userId, AIProfile.AIProfileStatus status);
}