package com.easyluxury.ai.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.rag.RAGService;
import com.easyluxury.entity.UserBehavior;
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
 * BehaviorTrackingService
 * 
 * Advanced service for comprehensive user behavior tracking, analysis,
 * and pattern recognition using AI-powered insights.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BehaviorTrackingService {
    
    private final AICoreService aiCoreService;
    private final RAGService ragService;
    private final UserBehaviorRepository userBehaviorRepository;
    private final AIHelperService aiHelperService;
    private final SimpleAIService simpleAIService;
    
    /**
     * Track comprehensive user behavior with AI analysis
     * 
     * @param userId the user ID
     * @param behaviorType the type of behavior
     * @param entityType the entity type being interacted with
     * @param entityId the entity ID
     * @param action the action performed
     * @param context additional context
     * @param metadata behavior metadata
     * @param sessionId the session ID
     * @param deviceInfo device information
     * @param locationInfo location information
     * @return tracked behavior with AI analysis
     */
    @Transactional
    public UserBehavior trackBehavior(
            UUID userId,
            String behaviorType,
            String entityType,
            String entityId,
            String action,
            String context,
            Map<String, Object> metadata,
            String sessionId,
            Map<String, Object> deviceInfo,
            Map<String, Object> locationInfo) {
        
        try {
            log.debug("Tracking behavior for user: {} - {} on {}", userId, behaviorType, entityType);
            
            // Create behavior record
            UserBehavior behavior = UserBehavior.builder()
                .userId(userId)
                .behaviorType(UserBehavior.BehaviorType.valueOf(behaviorType.toUpperCase()))
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .context(context)
                .metadata(metadata != null ? metadata.toString() : null)
                .sessionId(sessionId)
                .deviceInfo(deviceInfo != null ? deviceInfo.toString() : null)
                .locationInfo(locationInfo != null ? locationInfo.toString() : null)
                .createdAt(LocalDateTime.now())
                .build();
            
            // Perform AI analysis on the behavior
            String aiAnalysis = analyzeBehaviorWithAI(behavior);
            behavior.setAiAnalysis(aiAnalysis);
            
            // Calculate behavior significance score
            Double significanceScore = calculateBehaviorSignificance(behavior);
            behavior.setSignificanceScore(significanceScore);
            
            // Detect patterns and flags
            String patternFlags = detectBehaviorPatterns(behavior);
            behavior.setPatternFlags(patternFlags);
            
            // Generate AI insights
            String aiInsights = generateBehaviorInsights(behavior);
            behavior.setAiInsights(aiInsights);
            
            // Calculate behavior score
            Double behaviorScore = calculateBehaviorScore(behavior);
            behavior.setBehaviorScore(behaviorScore);
            
            // Save the behavior
            behavior = userBehaviorRepository.save(behavior);
            
            // Update user behavior profile
            updateUserBehaviorProfile(userId, behavior);
            
            log.debug("Successfully tracked behavior for user: {} with score: {}", userId, behaviorScore);
            
            return behavior;
            
        } catch (Exception e) {
            log.error("Error tracking behavior for user: {}", userId, e);
            throw new RuntimeException("Failed to track behavior", e);
        }
    }
    
    /**
     * Analyze behavior patterns for a user
     * 
     * @param userId the user ID
     * @param days number of days to analyze
     * @return behavior pattern analysis
     */
    @Transactional(readOnly = true)
    public String analyzeBehaviorPatterns(UUID userId, int days) {
        try {
            log.debug("Analyzing behavior patterns for user: {} over {} days", userId, days);
            
            // Get recent behaviors
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
                    userId, 
                    LocalDateTime.now().minusDays(days)
                );
            
            if (behaviors.isEmpty()) {
                return "No behavior data available for analysis";
            }
            
            // Generate AI analysis of patterns
            String patternAnalysis = aiHelperService.generateContent(
                "Analyze the following user behaviors and identify patterns, trends, and insights:\n" +
                behaviors.stream()
                    .map(b -> String.format("Type: %s, Action: %s, Entity: %s, Time: %s", 
                        b.getBehaviorType(), b.getAction(), b.getEntityType(), b.getCreatedAt()))
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("No behaviors"),
                "user_behavior",
                "pattern_analysis"
            );
            
            log.debug("Successfully analyzed behavior patterns for user: {}", userId);
            
            return patternAnalysis;
            
        } catch (Exception e) {
            log.error("Error analyzing behavior patterns for user: {}", userId, e);
            throw new RuntimeException("Failed to analyze behavior patterns", e);
        }
    }
    
    /**
     * Detect behavioral anomalies
     * 
     * @param userId the user ID
     * @return anomaly detection results
     */
    @Transactional(readOnly = true)
    public String detectBehavioralAnomalies(UUID userId) {
        try {
            log.debug("Detecting behavioral anomalies for user: {}", userId);
            
            // Get recent behaviors
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(100)
                .toList();
            
            if (behaviors.size() < 10) {
                return "Insufficient data for anomaly detection";
            }
            
            // Generate AI analysis for anomaly detection
            String anomalyAnalysis = aiHelperService.generateContent(
                "Analyze the following user behaviors for anomalies, unusual patterns, or suspicious activities:\n" +
                behaviors.stream()
                    .map(b -> String.format("Type: %s, Action: %s, Entity: %s, Time: %s, Score: %s", 
                        b.getBehaviorType(), b.getAction(), b.getEntityType(), b.getCreatedAt(), b.getBehaviorScore()))
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("No behaviors"),
                "user_behavior",
                "anomaly_detection"
            );
            
            log.debug("Successfully detected behavioral anomalies for user: {}", userId);
            
            return anomalyAnalysis;
            
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
            
            // Get comprehensive behavior data
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(200)
                .toList();
            
            if (behaviors.isEmpty()) {
                return "No behavior data available for insights generation";
            }
            
            // Generate AI insights
            String insights = aiHelperService.generateContent(
                "Generate comprehensive behavioral insights for this user based on their activity patterns:\n" +
                behaviors.stream()
                    .map(b -> String.format("Type: %s, Action: %s, Entity: %s, Time: %s, Context: %s", 
                        b.getBehaviorType(), b.getAction(), b.getEntityType(), b.getCreatedAt(), b.getContext()))
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("No behaviors"),
                "user_behavior",
                "insights_generation"
            );
            
            log.debug("Successfully generated behavioral insights for user: {}", userId);
            
            return insights;
            
        } catch (Exception e) {
            log.error("Error generating behavioral insights for user: {}", userId, e);
            throw new RuntimeException("Failed to generate behavioral insights", e);
        }
    }
    
    /**
     * Get behavior analytics for a user
     * 
     * @param userId the user ID
     * @param days number of days to analyze
     * @return behavior analytics
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getBehaviorAnalytics(UUID userId, int days) {
        try {
            log.debug("Getting behavior analytics for user: {} over {} days", userId, days);
            
            // Get behaviors for the period
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
                    userId, 
                    LocalDateTime.now().minusDays(days)
                );
            
            // Calculate analytics
            long totalBehaviors = behaviors.size();
            double avgBehaviorScore = behaviors.stream()
                .mapToDouble(b -> b.getBehaviorScore() != null ? b.getBehaviorScore() : 0.0)
                .average()
                .orElse(0.0);
            
            long uniqueEntityTypes = behaviors.stream()
                .map(UserBehavior::getEntityType)
                .distinct()
                .count();
            
            long uniqueActions = behaviors.stream()
                .map(UserBehavior::getAction)
                .distinct()
                .count();
            
            // Most common behavior type
            String mostCommonBehavior = behaviors.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    b -> b.getBehaviorType().name(),
                    java.util.stream.Collectors.counting()
                ))
                .entrySet()
                .stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("UNKNOWN");
            
            Map<String, Object> analytics = Map.of(
                "totalBehaviors", totalBehaviors,
                "avgBehaviorScore", avgBehaviorScore,
                "uniqueEntityTypes", uniqueEntityTypes,
                "uniqueActions", uniqueActions,
                "mostCommonBehavior", mostCommonBehavior,
                "analysisPeriodDays", days,
                "analysisTimestamp", LocalDateTime.now()
            );
            
            log.debug("Successfully generated behavior analytics for user: {}", userId);
            
            return analytics;
            
        } catch (Exception e) {
            log.error("Error getting behavior analytics for user: {}", userId, e);
            throw new RuntimeException("Failed to get behavior analytics", e);
        }
    }
    
    /**
     * Analyze behavior with AI
     */
    private String analyzeBehaviorWithAI(UserBehavior behavior) {
        try {
            return aiHelperService.generateContent(
                String.format("Analyze this user behavior: Type=%s, Action=%s, Entity=%s, Context=%s", 
                    behavior.getBehaviorType(), behavior.getAction(), behavior.getEntityType(), behavior.getContext()),
                "user_behavior",
                "behavior_analysis"
            );
        } catch (Exception e) {
            log.warn("Failed to analyze behavior with AI", e);
            return "AI analysis unavailable";
        }
    }
    
    /**
     * Calculate behavior significance score
     */
    private Double calculateBehaviorSignificance(UserBehavior behavior) {
        // Simple scoring algorithm - can be enhanced with ML
        double score = 0.5; // Base score
        
        // Adjust based on behavior type
        switch (behavior.getBehaviorType()) {
            case PURCHASE -> score += 0.3;
            case SEARCH -> score += 0.2;
            case VIEW -> score += 0.1;
            case CLICK -> score += 0.1;
            case LOGIN -> score += 0.2;
            default -> score += 0.05;
        }
        
        // Adjust based on context
        if (behavior.getContext() != null && behavior.getContext().contains("important")) {
            score += 0.1;
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Detect behavior patterns
     */
    private String detectBehaviorPatterns(UserBehavior behavior) {
        // Simple pattern detection - can be enhanced with ML
        List<String> patterns = new java.util.ArrayList<>();
        
        if (behavior.getBehaviorType() == UserBehavior.BehaviorType.PURCHASE) {
            patterns.add("PURCHASE_PATTERN");
        }
        
        if (behavior.getAction() != null && behavior.getAction().contains("search")) {
            patterns.add("SEARCH_PATTERN");
        }
        
        return String.join(",", patterns);
    }
    
    /**
     * Generate behavior insights
     */
    private String generateBehaviorInsights(UserBehavior behavior) {
        try {
            return aiHelperService.generateContent(
                String.format("Generate insights for this behavior: %s %s on %s", 
                    behavior.getBehaviorType(), behavior.getAction(), behavior.getEntityType()),
                "user_behavior",
                "insights_generation"
            );
        } catch (Exception e) {
            log.warn("Failed to generate behavior insights", e);
            return "Insights unavailable";
        }
    }
    
    /**
     * Calculate behavior score
     */
    private Double calculateBehaviorScore(UserBehavior behavior) {
        // Simple scoring - can be enhanced with ML
        double score = 0.5;
        
        if (behavior.getSignificanceScore() != null) {
            score = behavior.getSignificanceScore();
        }
        
        return score;
    }
    
    /**
     * Update user behavior profile
     */
    private void updateUserBehaviorProfile(UUID userId, UserBehavior behavior) {
        // This would update the user's behavior profile in the database
        // Implementation depends on the User entity structure
        log.debug("Updating behavior profile for user: {}", userId);
    }
}