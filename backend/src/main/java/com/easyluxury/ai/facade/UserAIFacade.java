package com.easyluxury.ai.facade;

import com.easyluxury.ai.dto.UserAIInsightsRequest;
import com.easyluxury.ai.dto.UserAIInsightsResponse;
import com.easyluxury.ai.dto.UserBehaviorRequest;
import com.easyluxury.ai.dto.UserBehaviorResponse;
import com.easyluxury.ai.dto.UserAIRecommendationRequest;
import com.easyluxury.ai.dto.UserAIRecommendationResponse;
import com.easyluxury.ai.mapper.UserAIMapper;
import com.easyluxury.ai.service.UserAIService;
import com.easyluxury.ai.service.UserBehaviorService;
import com.easyluxury.entity.User;
import com.easyluxury.entity.UserBehavior;
import com.easyluxury.repository.UserRepository;
import com.easyluxury.repository.UserBehaviorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * UserAIFacade
 * 
 * Facade for user-specific AI operations providing a high-level interface
 * for AI-powered user features including behavioral tracking, insights, and recommendations.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAIFacade {
    
    private final UserAIService userAIService;
    private final UserBehaviorService userBehaviorService;
    private final UserRepository userRepository;
    private final UserBehaviorRepository userBehaviorRepository;
    private final UserAIMapper userAIMapper;
    
    /**
     * Track user behavior and generate insights
     * 
     * @param request the behavior tracking request
     * @return behavior tracking response
     */
    @Transactional
    public UserBehaviorResponse trackUserBehavior(UserBehaviorRequest request) {
        try {
            log.debug("Tracking user behavior for user: {} - {}", 
                request.getUserId(), request.getBehaviorType());
            
            // Track behavior using UserAIService
            userAIService.trackUserBehavior(
                request.getUserId(),
                request.getBehaviorType(),
                request.getEntityType(),
                request.getEntityId(),
                request.getAction(),
                request.getContext(),
                request.getMetadata()
            );
            
            // Create behavior record
            UserBehavior behavior = userAIMapper.toUserBehavior(request);
            behavior = userBehaviorRepository.save(behavior);
            
            // Convert to response
            UserBehaviorResponse response = userAIMapper.toUserBehaviorResponse(behavior);
            
            log.debug("Successfully tracked user behavior for user: {}", request.getUserId());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error tracking user behavior for user: {}", request.getUserId(), e);
            throw new RuntimeException("Failed to track user behavior", e);
        }
    }
    
    /**
     * Get comprehensive user AI insights
     * 
     * @param request the insights request
     * @return user AI insights
     */
    @Transactional(readOnly = true)
    public UserAIInsightsResponse getUserInsights(UserAIInsightsRequest request) {
        try {
            log.debug("Getting AI insights for user: {}", request.getUserId());
            
            User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + request.getUserId()));
            
            // Generate insights based on analysis type
            String insights = switch (request.getAnalysisType()) {
                case "behavioral" -> userAIService.analyzeUserBehavior(request.getUserId());
                case "preferences" -> userAIService.learnUserPreferences(request.getUserId());
                case "recommendations" -> userAIService.generateUserRecommendations(request.getUserId(), 10);
                default -> userAIService.analyzeUserBehavior(request.getUserId());
            };
            
            // Get behavior patterns if requested
            String patterns = null;
            if (request.getIncludePatterns()) {
                patterns = userBehaviorService.analyzeBehaviorPatterns(request.getUserId(), request.getDays());
            }
            
            // Get anomalies if requested
            String anomalies = null;
            if (request.getIncludeAnomalies()) {
                anomalies = userBehaviorService.detectBehavioralAnomalies(request.getUserId());
            }
            
            // Calculate behavior score
            Double behaviorScore = userBehaviorService.calculateBehaviorScore(request.getUserId());
            
            // Get user statistics
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(request.getUserId());
            
            // Convert to response
            UserAIInsightsResponse response = UserAIInsightsResponse.builder()
                .userId(request.getUserId())
                .insights(insights)
                .patterns(patterns)
                .anomalies(anomalies)
                .behaviorScore(behaviorScore)
                .totalInteractions((long) behaviors.size())
                .lastActivityAt(user.getLastActivityAt())
                .analysisTimestamp(java.time.LocalDateTime.now())
                .analysisType(request.getAnalysisType())
                .build();
            
            log.debug("Successfully generated AI insights for user: {}", request.getUserId());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error getting AI insights for user: {}", request.getUserId(), e);
            throw new RuntimeException("Failed to get user insights", e);
        }
    }
    
    /**
     * Generate user recommendations
     * 
     * @param request the recommendation request
     * @return user recommendations
     */
    @Transactional(readOnly = true)
    public UserAIRecommendationResponse generateUserRecommendations(UserAIRecommendationRequest request) {
        try {
            log.debug("Generating recommendations for user: {}", request.getUserId());
            
            // Generate AI recommendations
            String recommendations = userAIService.generateUserRecommendations(
                request.getUserId(), 
                request.getLimit()
            );
            
            // Get user preferences
            String preferences = userAIService.learnUserPreferences(request.getUserId());
            
            // Convert to response
            UserAIRecommendationResponse response = UserAIRecommendationResponse.builder()
                .userId(request.getUserId())
                .recommendations(recommendations)
                .preferences(preferences)
                .totalRecommendations(request.getLimit())
                .generationTimestamp(java.time.LocalDateTime.now())
                .build();
            
            log.debug("Successfully generated recommendations for user: {}", request.getUserId());
            
            return response;
            
        } catch (Exception e) {
            log.error("Error generating recommendations for user: {}", request.getUserId(), e);
            throw new RuntimeException("Failed to generate user recommendations", e);
        }
    }
    
    /**
     * Analyze user behavior patterns
     * 
     * @param userId the user ID
     * @param days number of days to analyze
     * @return behavior pattern analysis
     */
    @Transactional(readOnly = true)
    public String analyzeBehaviorPatterns(UUID userId, int days) {
        try {
            log.debug("Analyzing behavior patterns for user: {} over {} days", userId, days);
            
            String patterns = userBehaviorService.analyzeBehaviorPatterns(userId, days);
            
            log.debug("Successfully analyzed behavior patterns for user: {}", userId);
            
            return patterns;
            
        } catch (Exception e) {
            log.error("Error analyzing behavior patterns for user: {}", userId, e);
            throw new RuntimeException("Failed to analyze behavior patterns", e);
        }
    }
    
    /**
     * Detect user behavioral anomalies
     * 
     * @param userId the user ID
     * @return anomaly detection results
     */
    @Transactional(readOnly = true)
    public String detectBehavioralAnomalies(UUID userId) {
        try {
            log.debug("Detecting behavioral anomalies for user: {}", userId);
            
            String anomalies = userBehaviorService.detectBehavioralAnomalies(userId);
            
            log.debug("Successfully detected behavioral anomalies for user: {}", userId);
            
            return anomalies;
            
        } catch (Exception e) {
            log.error("Error detecting behavioral anomalies for user: {}", userId, e);
            throw new RuntimeException("Failed to detect behavioral anomalies", e);
        }
    }
    
    /**
     * Generate behavioral insights
     * 
     * @param userId the user ID
     * @return behavioral insights
     */
    @Transactional(readOnly = true)
    public String generateBehavioralInsights(UUID userId) {
        try {
            log.debug("Generating behavioral insights for user: {}", userId);
            
            String insights = userBehaviorService.generateBehavioralInsights(userId);
            
            log.debug("Successfully generated behavioral insights for user: {}", userId);
            
            return insights;
            
        } catch (Exception e) {
            log.error("Error generating behavioral insights for user: {}", userId, e);
            throw new RuntimeException("Failed to generate behavioral insights", e);
        }
    }
    
    /**
     * Get user behavior history
     * 
     * @param userId the user ID
     * @param limit maximum number of behaviors to return
     * @return user behavior history
     */
    @Transactional(readOnly = true)
    public List<UserBehaviorResponse> getUserBehaviorHistory(UUID userId, int limit) {
        try {
            log.debug("Getting behavior history for user: {}", userId);
            
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(limit)
                .toList();
            
            List<UserBehaviorResponse> responses = userAIMapper.toUserBehaviorResponseList(behaviors);
            
            log.debug("Retrieved {} behaviors for user: {}", responses.size(), userId);
            
            return responses;
            
        } catch (Exception e) {
            log.error("Error getting behavior history for user: {}", userId, e);
            throw new RuntimeException("Failed to get user behavior history", e);
        }
    }
}