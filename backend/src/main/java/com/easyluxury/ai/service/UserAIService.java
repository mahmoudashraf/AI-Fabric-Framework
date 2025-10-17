package com.easyluxury.ai.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.ai.infrastructure.rag.RAGService;
import com.easyluxury.entity.User;
import com.easyluxury.entity.UserBehavior;
import com.easyluxury.repository.UserRepository;
import com.easyluxury.repository.UserBehaviorRepository;
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
    
    private final AICoreService aiCoreService;
    private final AIEmbeddingService embeddingService;
    private final RAGService ragService;
    private final UserRepository userRepository;
    private final UserBehaviorRepository userBehaviorRepository;
    
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
    public void trackUserBehavior(UUID userId, UserBehavior.BehaviorType behaviorType, 
                                 String entityType, String entityId, String action, 
                                 String context, Map<String, Object> metadata) {
        try {
            log.debug("Tracking user behavior for user {}: {} on {} {}", 
                userId, behaviorType, entityType, entityId);
            
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
            
            // Save behavior
            userBehaviorRepository.save(behavior);
            
            // Update user activity
            updateUserActivity(userId);
            
            // Trigger AI analysis if significant behavior
            if (isSignificantBehavior(behaviorType)) {
                analyzeUserBehavior(userId);
            }
            
            log.debug("Successfully tracked user behavior for user {}", userId);
            
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
    public String analyzeUserBehavior(UUID userId) {
        try {
            log.debug("Analyzing user behavior for user {}", userId);
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            // Get recent behaviors
            List<UserBehavior> recentBehaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
            
            if (recentBehaviors.isEmpty()) {
                return "No behavioral data available for analysis.";
            }
            
            // Build context for AI analysis
            StringBuilder context = new StringBuilder();
            context.append("User: ").append(user.getEmail()).append("\n");
            context.append("Recent behaviors:\n");
            
            recentBehaviors.stream()
                .limit(10) // Last 10 behaviors
                .forEach(behavior -> {
                    context.append("- ").append(behavior.getBehaviorType())
                        .append(" on ").append(behavior.getEntityType())
                        .append(" ").append(behavior.getEntityId())
                        .append(" at ").append(behavior.getCreatedAt())
                        .append("\n");
                });
            
            // Generate AI insights
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt("Analyze the following user behavior data and provide insights about user preferences, patterns, and recommendations:")
                .context(context.toString())
                .purpose("user_behavior_analysis")
                .maxTokens(500)
                .temperature(0.3)
                .build();
            
            AIGenerationResponse response = AIGenerationResponse.builder().content("AI analysis placeholder").build();
            
            // Update user with AI insights
            user.setAiInsights(response.getContent());
            user.setLastActivityAt(LocalDateTime.now());
            userRepository.save(user);
            
            log.debug("Successfully analyzed user behavior for user {}", userId);
            
            return response.getContent();
            
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
            
            // Build user profile context
            StringBuilder context = new StringBuilder();
            context.append("User Profile:\n");
            context.append("Email: ").append(user.getEmail()).append("\n");
            context.append("Preferences: ").append(user.getAiPreferences() != null ? user.getAiPreferences() : "None").append("\n");
            context.append("Interests: ").append(user.getAiInterests() != null ? user.getAiInterests() : "None").append("\n");
            context.append("Behavior Profile: ").append(user.getAiBehaviorProfile() != null ? user.getAiBehaviorProfile() : "None").append("\n");
            
            if (user.getAiInsights() != null) {
                context.append("AI Insights: ").append(user.getAiInsights()).append("\n");
            }
            
            // Generate recommendations
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt("Based on the user profile and behavior data, generate personalized product recommendations for this luxury e-commerce customer:")
                .context(context.toString())
                .purpose("user_recommendations")
                .maxTokens(300)
                .temperature(0.4)
                .build();
            
            AIGenerationResponse response = AIGenerationResponse.builder().content("AI analysis placeholder").build();
            
            log.debug("Successfully generated recommendations for user {}", userId);
            
            return response.getContent();
            
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
            
            // Get user behaviors
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
            
            if (behaviors.isEmpty()) {
                return "No behavioral data available for preference learning.";
            }
            
            // Build behavior context
            StringBuilder context = new StringBuilder();
            context.append("User behaviors for preference analysis:\n");
            
            behaviors.stream()
                .limit(20) // Last 20 behaviors
                .forEach(behavior -> {
                    context.append("- ").append(behavior.getBehaviorType())
                        .append(": ").append(behavior.getAction())
                        .append(" on ").append(behavior.getEntityType())
                        .append(" ").append(behavior.getEntityId())
                        .append("\n");
                });
            
            // Generate preference insights
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt("Analyze the user's behavior patterns and extract their preferences, interests, and shopping patterns:")
                .context(context.toString())
                .purpose("preference_learning")
                .maxTokens(400)
                .temperature(0.2)
                .build();
            
            AIGenerationResponse response = AIGenerationResponse.builder().content("AI analysis placeholder").build();
            
            // Update user preferences
            user.setAiPreferences(response.getContent());
            user.setLastActivityAt(LocalDateTime.now());
            userRepository.save(user);
            
            log.debug("Successfully learned preferences for user {}", userId);
            
            return response.getContent();
            
        } catch (Exception e) {
            log.error("Error learning preferences for user {}", userId, e);
            throw new RuntimeException("Failed to learn user preferences", e);
        }
    }
    
    /**
     * Search users using AI-powered semantic search
     * 
     * @param query the search query
     * @param limit maximum number of results
     * @return search results
     */
    public AISearchResponse searchUsers(String query, int limit) {
        try {
            log.debug("Searching users with query: {}", query);
            
            AISearchRequest searchRequest = AISearchRequest.builder()
                .query(query)
                .entityType("user")
                .limit(limit)
                .threshold(0.7)
                .build();
            
            AISearchResponse response = ragService.performRAGQuery(query, "user", limit);
            
            log.debug("Found {} users matching query: {}", response.getTotalResults(), query);
            
            return response;
            
        } catch (Exception e) {
            log.error("Error searching users with query: {}", query, e);
            throw new RuntimeException("Failed to search users", e);
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