package com.easyluxury.repository;

import com.easyluxury.entity.UserBehavior;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * UserBehaviorRepository
 * 
 * Repository interface for UserBehavior entity operations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Repository
public interface UserBehaviorRepository extends JpaRepository<UserBehavior, UUID> {
    
    /**
     * Find behaviors by user ID ordered by creation date descending
     */
    List<UserBehavior> findByUserIdOrderByCreatedAtDesc(UUID userId);
    
    /**
     * Find behaviors by user ID and created after specified date
     */
    List<UserBehavior> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(UUID userId, LocalDateTime createdAfter);
    
    /**
     * Find behaviors by user ID and behavior type
     */
    List<UserBehavior> findByUserIdAndBehaviorTypeOrderByCreatedAtDesc(UUID userId, UserBehavior.BehaviorType behaviorType);
    
    /**
     * Find behaviors by user ID and entity type
     */
    List<UserBehavior> findByUserIdAndEntityTypeOrderByCreatedAtDesc(UUID userId, String entityType);
    
    /**
     * Count behaviors by user ID and behavior type
     */
    long countByUserIdAndBehaviorType(UUID userId, UserBehavior.BehaviorType behaviorType);
    
    /**
     * Find recent behaviors by user ID within specified hours
     */
    @Query("SELECT ub FROM UserBehavior ub WHERE ub.userId = :userId AND ub.createdAt >= :since ORDER BY ub.createdAt DESC")
    List<UserBehavior> findRecentBehaviorsByUser(@Param("userId") UUID userId, @Param("since") LocalDateTime since);
    
    /**
     * Find behaviors by behavior type within date range
     */
    @Query("SELECT ub FROM UserBehavior ub WHERE ub.behaviorType = :behaviorType AND ub.createdAt BETWEEN :startDate AND :endDate ORDER BY ub.createdAt DESC")
    List<UserBehavior> findByBehaviorTypeAndDateRange(@Param("behaviorType") UserBehavior.BehaviorType behaviorType, 
                                                     @Param("startDate") LocalDateTime startDate, 
                                                     @Param("endDate") LocalDateTime endDate);
    
    /**
     * Find top entity types by user ID
     */
    @Query("SELECT ub.entityType, COUNT(ub) FROM UserBehavior ub WHERE ub.userId = :userId AND ub.entityType IS NOT NULL GROUP BY ub.entityType ORDER BY COUNT(ub) DESC")
    List<Object[]> findTopEntityTypesByUser(@Param("userId") UUID userId);
    
    /**
     * Find behavior statistics by user ID
     */
    @Query("SELECT ub.behaviorType, COUNT(ub), AVG(ub.behaviorScore) FROM UserBehavior ub WHERE ub.userId = :userId GROUP BY ub.behaviorType")
    List<Object[]> findBehaviorStatisticsByUser(@Param("userId") UUID userId);
}