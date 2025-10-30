package com.easyluxury.ai.adapter;

import com.ai.infrastructure.service.BehaviorService;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.ai.infrastructure.dto.BehaviorAnalysisResult;
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
import java.util.stream.Collectors;

/**
 * User Behavior Adapter
 * 
 * Adapter that bridges EasyLuxury domain-specific UserBehavior entity
 * with the generic AI Infrastructure BehaviorService.
 * 
 * This adapter delegates to the AI module's generic BehaviorService while
 * maintaining compatibility with EasyLuxury's domain model.
 * 
 * @author EasyLuxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserBehaviorAdapter {
    
    private final BehaviorService behaviorService;
    private final UserBehaviorRepository userBehaviorRepository;
    
    /**
     * Analyze behavior patterns for a specific user
     * Delegates to generic BehaviorService
     * 
     * @param userId the user ID
     * @param days number of days to analyze
     * @return behavior analysis insights
     */
    @Transactional(readOnly = true)
    public String analyzeBehaviorPatterns(UUID userId, int days) {
        try {
            log.debug("Analyzing behavior patterns for user {} over {} days", userId, days);
            
            LocalDateTime startDate = LocalDateTime.now().minusDays(days);
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(userId, startDate);
            
            if (behaviors.isEmpty()) {
                return "No behavioral data available for the specified period.";
            }
            
            // Use generic BehaviorService for analysis
            BehaviorAnalysisResult analysisResult = behaviorService.analyzeBehaviors(userId);
            
            // Build comprehensive summary
            StringBuilder summary = new StringBuilder();
            summary.append("Behavior Analysis Summary:\n\n");
            summary.append(String.format("- Analysis Type: %s\n", analysisResult.getAnalysisType()));
            summary.append(String.format("- Summary: %s\n", analysisResult.getSummary()));
            summary.append(String.format("- Confidence Score: %.2f\n", analysisResult.getConfidenceScore()));
            summary.append(String.format("- Significance Score: %.2f\n\n", analysisResult.getSignificanceScore()));
            
            if (!analysisResult.getInsights().isEmpty()) {
                summary.append("Key Insights:\n");
                analysisResult.getInsights().forEach(insight -> 
                    summary.append(String.format("  • %s\n", insight))
                );
            }
            
            if (!analysisResult.getPatterns().isEmpty()) {
                summary.append("\nDetected Patterns:\n");
                analysisResult.getPatterns().forEach(pattern -> 
                    summary.append(String.format("  • %s\n", pattern))
                );
            }
            
            if (!analysisResult.getRecommendations().isEmpty()) {
                summary.append("\nRecommendations:\n");
                analysisResult.getRecommendations().forEach(recommendation -> 
                    summary.append(String.format("  • %s\n", recommendation))
                );
            }
            
            log.debug("Successfully analyzed behavior patterns for user {}", userId);
            
            return summary.toString();
            
        } catch (Exception e) {
            log.error("Error analyzing behavior patterns for user {}", userId, e);
            throw new RuntimeException("Failed to analyze behavior patterns", e);
        }
    }
    
    /**
     * Detect behavioral anomalies
     * Delegates to generic BehaviorService
     * 
     * @param userId the user ID
     * @return anomaly detection results
     */
    @Transactional(readOnly = true)
    public String detectBehavioralAnomalies(UUID userId) {
        try {
            log.debug("Detecting behavioral anomalies for user {}", userId);
            
            List<UserBehavior> recentBehaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
            
            if (recentBehaviors.size() < 5) {
                return "Insufficient behavioral data for anomaly detection.";
            }
            
            // Use generic BehaviorService for analysis
            BehaviorAnalysisResult analysisResult = behaviorService.analyzeBehaviors(userId);
            
            // Extract anomaly-related insights
            String anomalyInsights = analysisResult.getInsights().stream()
                .filter(insight -> insight.toLowerCase().contains("anomaly") || 
                                 insight.toLowerCase().contains("unusual") ||
                                 insight.toLowerCase().contains("unexpected"))
                .collect(Collectors.joining("\n"));
            
            if (anomalyInsights.isEmpty()) {
                return "No significant anomalies detected in user behavior.";
            }
            
            log.debug("Successfully detected behavioral anomalies for user {}", userId);
            
            return "Behavioral Anomalies Detected:\n\n" + anomalyInsights;
            
        } catch (Exception e) {
            log.error("Error detecting behavioral anomalies for user {}", userId, e);
            throw new RuntimeException("Failed to detect behavioral anomalies", e);
        }
    }
    
    /**
     * Generate behavioral insights and recommendations
     * Delegates to generic BehaviorService
     * 
     * @param userId the user ID
     * @return behavioral insights
     */
    @Transactional(readOnly = true)
    public String generateBehavioralInsights(UUID userId) {
        try {
            log.debug("Generating behavioral insights for user {}", userId);
            
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
            
            if (behaviors.isEmpty()) {
                return "No behavioral data available for insights generation.";
            }
            
            // Use generic BehaviorService for analysis
            BehaviorAnalysisResult analysisResult = behaviorService.analyzeBehaviors(userId);
            
            // Format insights
            StringBuilder insights = new StringBuilder();
            insights.append("Behavioral Insights:\n\n");
            
            if (!analysisResult.getInsights().isEmpty()) {
                insights.append("Insights:\n");
                analysisResult.getInsights().forEach(insight -> 
                    insights.append(String.format("  • %s\n", insight))
                );
                insights.append("\n");
            }
            
            if (!analysisResult.getRecommendations().isEmpty()) {
                insights.append("Recommendations:\n");
                analysisResult.getRecommendations().forEach(recommendation -> 
                    insights.append(String.format("  • %s\n", recommendation))
                );
            }
            
            log.debug("Successfully generated behavioral insights for user {}", userId);
            
            return insights.toString();
            
        } catch (Exception e) {
            log.error("Error generating behavioral insights for user {}", userId, e);
            throw new RuntimeException("Failed to generate behavioral insights", e);
        }
    }
    
    /**
     * Calculate behavior score for a user
     * Uses AI module's confidence score
     * 
     * @param userId the user ID
     * @return calculated behavior score
     */
    @Transactional(readOnly = true)
    public Double calculateBehaviorScore(UUID userId) {
        try {
            log.debug("Calculating behavior score for user {}", userId);
            
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
            
            if (behaviors.isEmpty()) {
                return 0.0;
            }
            
            // Use generic BehaviorService for analysis
            BehaviorAnalysisResult analysisResult = behaviorService.analyzeBehaviors(userId);
            
            // Use confidence score as behavior score
            double score = analysisResult.getConfidenceScore();
            
            log.debug("Calculated behavior score {} for user {}", score, userId);
            
            return score;
            
        } catch (Exception e) {
            log.error("Error calculating behavior score for user {}", userId, e);
            throw new RuntimeException("Failed to calculate behavior score", e);
        }
    }
    
    /**
     * Get behavior weight for scoring
     * Helper method for backward compatibility
     * 
     * @param behaviorType the behavior type
     * @return weight score
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
}
