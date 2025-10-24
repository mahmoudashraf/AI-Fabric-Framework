package com.easyluxury.ai.service;

import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.ai.infrastructure.dto.AIProfileResponse;
import com.easyluxury.ai.adapter.UserAIAdapter;
import com.easyluxury.entity.User;
import com.easyluxury.entity.UserBehavior;
import com.easyluxury.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * UserAIService
 * 
 * Provides AI-powered functionality for User entities including behavioral tracking,
 * preference learning, and personalized recommendations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAIService {
    
    private final UserAIAdapter userAIAdapter;
    private final UserRepository userRepository;
    
    /**
     * Track user behavior for AI analysis
     * 
     * @param userId the user ID
     * @param behaviorType the type of behavior
     * @param entityType the type of entity involved
     * @param entityId the ID of the entity
     * @param action the action performed
     * @param context additional context
     * @param metadata additional metadata
     */
    @Transactional
    public BehaviorResponse trackUserBehavior(UUID userId, UserBehavior.BehaviorType behaviorType, 
                                 String entityType, String entityId, String action, 
                                 String context, Map<String, Object> metadata) {
        try {
            log.debug("Tracking user behavior for user {}: {} on {} {}", 
                userId, behaviorType, entityType, entityId);
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            // Create behavior record
            UserBehavior behavior = UserBehavior.builder()
                .userId(userId)
                .behaviorType(behaviorType)
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .context(context)
                .metadata(metadata != null ? metadata.toString() : null)
                .createdAt(LocalDateTime.now())
                .build();
            
            // Use AI adapter to track behavior
            BehaviorResponse response = userAIAdapter.trackUserBehavior(user, behavior);
            
            // Update user activity
            updateUserActivity(userId);
            
            // Trigger AI analysis if significant behavior
            if (isSignificantBehavior(behaviorType)) {
                analyzeUserBehavior(userId);
            }
            
            log.debug("Successfully tracked user behavior for user {}", userId);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error tracking user behavior for user {}", userId, e);
            throw new RuntimeException("Failed to track user behavior", e);
        }
    }
    
    /**
     * Analyze user behavior and generate insights
     * 
     * @param userId the user ID
     * @return AI-generated insights
     */
    @Transactional
    public BehaviorAnalysisResult analyzeUserBehavior(UUID userId) {
        try {
            log.debug("Analyzing user behavior for user {}", userId);
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            // Use AI adapter to analyze behaviors
            BehaviorAnalysisResult result = userAIAdapter.analyzeUserBehaviors(user);
            
            // Update user with AI insights
            user.setAiInsights(result.getSummary());
            user.setLastActivityAt(LocalDateTime.now());
            userRepository.save(user);
            
            log.debug("Successfully analyzed user behavior for user {}", userId);
            
            return result;
            
        } catch (Exception e) {
            log.error("Error analyzing user behavior for user {}", userId, e);
            throw new RuntimeException("Failed to analyze user behavior", e);
        }
    }
    
    /**
     * Generate user recommendations based on behavior and preferences
     * 
     * @param userId the user ID
     * @param limit maximum number of recommendations
     * @return AI-generated recommendations
     */
    @Transactional
    public String generateUserRecommendations(UUID userId, int limit) {
        try {
            log.debug("Generating recommendations for user {}", userId);
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            // Analyze user behaviors to get recommendations
            BehaviorAnalysisResult analysis = userAIAdapter.analyzeUserBehaviors(user);
            
            // Extract recommendations from analysis
            String recommendations = analysis.getRecommendations() != null && !analysis.getRecommendations().isEmpty() 
                ? String.join(", ", analysis.getRecommendations())
                : "No specific recommendations available at this time.";
            
            log.debug("Successfully generated recommendations for user {}", userId);
            
            return recommendations;
            
        } catch (Exception e) {
            log.error("Error generating recommendations for user {}", userId, e);
            throw new RuntimeException("Failed to generate user recommendations", e);
        }
    }
    
    /**
     * Learn user preferences from behavior data
     * 
     * @param userId the user ID
     * @return updated preferences
     */
    @Transactional
    public String learnUserPreferences(UUID userId) {
        try {
            log.debug("Learning preferences for user {}", userId);
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            // Analyze user behaviors to learn preferences
            BehaviorAnalysisResult analysis = userAIAdapter.analyzeUserBehaviors(user);
            
            // Extract insights as preferences
            String preferences = analysis.getInsights() != null && !analysis.getInsights().isEmpty() 
                ? String.join(", ", analysis.getInsights())
                : "No specific preferences identified at this time.";
            
            // Update user preferences
            user.setAiPreferences(preferences);
            user.setLastActivityAt(LocalDateTime.now());
            userRepository.save(user);
            
            log.debug("Successfully learned preferences for user {}", userId);
            
            return preferences;
            
        } catch (Exception e) {
            log.error("Error learning preferences for user {}", userId, e);
            throw new RuntimeException("Failed to learn user preferences", e);
        }
    }
    
    /**
     * Get user behaviors using AI infrastructure
     * 
     * @param userId the user ID
     * @return list of user behaviors
     */
    public List<BehaviorResponse> getUserBehaviors(UUID userId) {
        try {
            log.debug("Getting behaviors for user {}", userId);
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            return userAIAdapter.getUserBehaviors(user);
            
        } catch (Exception e) {
            log.error("Error getting behaviors for user {}", userId, e);
            throw new RuntimeException("Failed to get user behaviors", e);
        }
    }
    
    /**
     * Get user behaviors by type using AI infrastructure
     * 
     * @param userId the user ID
     * @param behaviorType the behavior type
     * @return list of user behaviors
     */
    public List<BehaviorResponse> getUserBehaviorsByType(UUID userId, UserBehavior.BehaviorType behaviorType) {
        try {
            log.debug("Getting behaviors for user {} and type {}", userId, behaviorType);
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            return userAIAdapter.getUserBehaviorsByType(user, behaviorType);
            
        } catch (Exception e) {
            log.error("Error getting behaviors for user {} and type {}", userId, behaviorType, e);
            throw new RuntimeException("Failed to get user behaviors by type", e);
        }
    }
    
    /**
     * Update user activity timestamp and interaction count
     */
    private void updateUserActivity(UUID userId) {
        try {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                user.setLastActivityAt(LocalDateTime.now());
                user.setTotalInteractions(user.getTotalInteractions() + 1);
                userRepository.save(user);
            }
        } catch (Exception e) {
            log.warn("Failed to update user activity for user {}", userId, e);
        }
    }
    
    /**
     * Check if behavior type is significant for AI analysis
     */
    private boolean isSignificantBehavior(UserBehavior.BehaviorType behaviorType) {
        return switch (behaviorType) {
            case PURCHASE, ADD_TO_CART, WISHLIST, REVIEW, RATING, 
                 SEARCH, PRODUCT_VIEW, CATEGORY_VIEW, BRAND_VIEW -> true;
            default -> false;
        };
    }
}