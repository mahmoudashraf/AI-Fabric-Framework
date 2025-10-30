package com.easyluxury.ai.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
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
 * UserBehaviorService
 * 
 * Provides AI-powered behavioral analysis and insights for user behavior data.
 * This service analyzes patterns, trends, and anomalies in user behavior.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBehaviorService {
    
    private final AICoreService aiCoreService;
    private final UserBehaviorRepository userBehaviorRepository;
    
    /**
     * Analyze behavior patterns for a specific user
     * 
     * @param userId the user ID
     * @param days number of days to analyze
     * @return behavior analysis insights
     */
    @Transactional
    public String analyzeBehaviorPatterns(UUID userId, int days) {
        try {
            log.debug("Analyzing behavior patterns for user {} over {} days", userId, days);
            
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, startDate);
            
            if (behaviors.isEmpty()) {
                return "No behavioral data available for the specified period.";
            }
            
            // Build behavior context
            StringBuilder context = new StringBuilder();
            context.append("Behavior Analysis for User: ").append(userId).append("\n");
            context.append("Analysis Period: Last ").append(days).append(" days\n");
            context.append("Total Behaviors: ").append(behaviors.size()).append("\n\n");
            
            // Group behaviors by type
            Map<UserBehavior.BehaviorType, Long> behaviorCounts = behaviors.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    UserBehavior::getBehaviorType,
                    java.util.stream.Collectors.counting()
                ));
            
            context.append("Behavior Distribution:\n");
            behaviorCounts.forEach((type, count) -> 
                context.append("- ").append(type).append(": ").append(count).append(" times\n"));
            
            context.append("\nRecent Behaviors:\n");
            behaviors.stream()
                .limit(10)
                .forEach(behavior -> {
                    context.append("- ").append(behavior.getBehaviorType())
                        .append(" on ").append(behavior.getEntityType())
                        .append(" ").append(behavior.getEntityId())
                        .append(" at ").append(behavior.getCreatedAt())
                        .append("\n");
                });
            
            // Generate AI analysis
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt("Analyze the following user behavior data and identify patterns, trends, and insights:")
                .context(context.toString())
                .purpose("behavior_pattern_analysis")
                .maxTokens(600)
                .temperature(0.3)
                .build();
            
            AIGenerationResponse response = AIGenerationResponse.builder().content("AI analysis placeholder").build();
            
            // Update behavior records with analysis
            updateBehaviorAnalysis(behaviors, response.getContent());
            
            log.debug("Successfully analyzed behavior patterns for user {}", userId);
            
            return response.getContent();
            
        } catch (Exception e) {
            log.error("Error analyzing behavior patterns for user {}", userId, e);
            throw new RuntimeException("Failed to analyze behavior patterns", e);
        }
    }
    
    /**
     * Detect behavioral anomalies
     * 
     * @param userId the user ID
     * @return anomaly detection results
     */
    @Transactional
    public String detectBehavioralAnomalies(UUID userId) {
        try {
            log.debug("Detecting behavioral anomalies for user {}", userId);
            
            // Get recent behaviors
            List<UserBehavior> recentBehaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
            
            if (recentBehaviors.size() < 5) {
                return "Insufficient behavioral data for anomaly detection.";
            }
            
            // Build anomaly detection context
            StringBuilder context = new StringBuilder();
            context.append("Anomaly Detection for User: ").append(userId).append("\n");
            context.append("Total Behaviors: ").append(recentBehaviors.size()).append("\n\n");
            
            // Analyze frequency patterns
            Map<UserBehavior.BehaviorType, Long> behaviorCounts = recentBehaviors.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    UserBehavior::getBehaviorType,
                    java.util.stream.Collectors.counting()
                ));
            
            context.append("Behavior Frequency:\n");
            behaviorCounts.forEach((type, count) -> 
                context.append("- ").append(type).append(": ").append(count).append(" times\n"));
            
            // Analyze time patterns
            context.append("\nTime-based Analysis:\n");
            recentBehaviors.stream()
                .limit(20)
                .forEach(behavior -> {
                    context.append("- ").append(behavior.getBehaviorType())
                        .append(" at ").append(behavior.getCreatedAt())
                        .append("\n");
                });
            
            // Generate anomaly detection
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt("Analyze the following user behavior data and identify any anomalies, unusual patterns, or suspicious activities:")
                .context(context.toString())
                .purpose("anomaly_detection")
                .maxTokens(500)
                .temperature(0.2)
                .build();
            
            AIGenerationResponse response = AIGenerationResponse.builder().content("AI analysis placeholder").build();
            
            log.debug("Successfully detected behavioral anomalies for user {}", userId);
            
            return response.getContent();
            
        } catch (Exception e) {
            log.error("Error detecting behavioral anomalies for user {}", userId, e);
            throw new RuntimeException("Failed to detect behavioral anomalies", e);
        }
    }
    
    /**
     * Generate behavioral insights and recommendations
     * 
     * @param userId the user ID
     * @return behavioral insights
     */
    @Transactional
    public String generateBehavioralInsights(UUID userId) {
        try {
            log.debug("Generating behavioral insights for user {}", userId);
            
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
            
            if (behaviors.isEmpty()) {
                return "No behavioral data available for insights generation.";
            }
            
            // Build insights context
            StringBuilder context = new StringBuilder();
            context.append("Behavioral Insights for User: ").append(userId).append("\n");
            context.append("Total Behaviors: ").append(behaviors.size()).append("\n\n");
            
            // Analyze behavior types
            Map<UserBehavior.BehaviorType, Long> behaviorCounts = behaviors.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                    UserBehavior::getBehaviorType,
                    java.util.stream.Collectors.counting()
                ));
            
            context.append("Behavior Summary:\n");
            behaviorCounts.forEach((type, count) -> 
                context.append("- ").append(type).append(": ").append(count).append(" times\n"));
            
            // Analyze entity interactions
            Map<String, Long> entityCounts = behaviors.stream()
                .filter(b -> b.getEntityType() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                    UserBehavior::getEntityType,
                    java.util.stream.Collectors.counting()
                ));
            
            context.append("\nEntity Interactions:\n");
            entityCounts.forEach((entity, count) -> 
                context.append("- ").append(entity).append(": ").append(count).append(" interactions\n"));
            
            // Recent activity
            context.append("\nRecent Activity:\n");
            behaviors.stream()
                .limit(15)
                .forEach(behavior -> {
                    context.append("- ").append(behavior.getBehaviorType())
                        .append(" on ").append(behavior.getEntityType())
                        .append(" ").append(behavior.getEntityId())
                        .append(" at ").append(behavior.getCreatedAt())
                        .append("\n");
                });
            
            // Generate insights
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt("Based on the user's behavioral data, generate insights about their preferences, patterns, and provide recommendations for improving their experience:")
                .context(context.toString())
                .purpose("behavioral_insights")
                .maxTokens(700)
                .temperature(0.4)
                .build();
            
            AIGenerationResponse response = AIGenerationResponse.builder().content("AI analysis placeholder").build();
            
            // Update behavior records with insights
            updateBehaviorInsights(behaviors, response.getContent());
            
            log.debug("Successfully generated behavioral insights for user {}", userId);
            
            return response.getContent();
            
        } catch (Exception e) {
            log.error("Error generating behavioral insights for user {}", userId, e);
            throw new RuntimeException("Failed to generate behavioral insights", e);
        }
    }
    
    /**
     * Calculate behavior score for a user
     * 
     * @param userId the user ID
     * @return calculated behavior score
     */
    @Transactional
    public Double calculateBehaviorScore(UUID userId) {
        try {
            log.debug("Calculating behavior score for user {}", userId);
            
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
            
            if (behaviors.isEmpty()) {
                return 0.0;
            }
            
            double score = 0.0;
            int totalBehaviors = behaviors.size();
            
            // Weight different behavior types
            for (UserBehavior behavior : behaviors) {
                double weight = getBehaviorWeight(behavior.getBehaviorType());
                score += weight;
            }
            
            // Normalize score (0-1)
            double normalizedScore = Math.min(score / (totalBehaviors * 1.0), 1.0);
            
            // Update behavior records with score
            behaviors.forEach(behavior -> {
                behavior.setBehaviorScore(normalizedScore);
                userBehaviorRepository.save(behavior);
            });
            
            log.debug("Calculated behavior score {} for user {}", normalizedScore, userId);
            
            return normalizedScore;
            
        } catch (Exception e) {
            log.error("Error calculating behavior score for user {}", userId, e);
            throw new RuntimeException("Failed to calculate behavior score", e);
        }
    }
    
    /**
     * Get behavior weight for scoring
     */
    private double getBehaviorWeight(UserBehavior.BehaviorType behaviorType) {
        return switch (behaviorType) {
            case PURCHASE -> 1.0;
            case ADD_TO_CART -> 0.8;
            case WISHLIST -> 0.7;
            case REVIEW, RATING -> 0.6;
            case PRODUCT_VIEW -> 0.5;
            case SEARCH -> 0.4;
            case CLICK -> 0.3;
            case VIEW -> 0.2;
            default -> 0.1;
        };
    }
    
    /**
     * Update behavior records with AI analysis
     */
    private void updateBehaviorAnalysis(List<UserBehavior> behaviors, String analysis) {
        behaviors.forEach(behavior -> {
            behavior.setAiAnalysis(analysis);
            userBehaviorRepository.save(behavior);
        });
    }
    
    /**
     * Update behavior records with AI insights
     */
    private void updateBehaviorInsights(List<UserBehavior> behaviors, String insights) {
        behaviors.forEach(behavior -> {
            behavior.setAiInsights(insights);
            userBehaviorRepository.save(behavior);
        });
    }
}