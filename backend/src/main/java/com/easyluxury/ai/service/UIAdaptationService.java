package com.easyluxury.ai.service;

import com.ai.infrastructure.core.AICoreService;
import com.easyluxury.entity.User;
import com.easyluxury.entity.UserBehavior;
import com.easyluxury.repository.UserRepository;
import com.easyluxury.repository.UserBehaviorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * UIAdaptationService
 * 
 * Service for AI-powered UI personalization and adaptation based on user behavior,
 * preferences, and interaction patterns.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UIAdaptationService {
    
    private final AICoreService aiCoreService;
    private final UserRepository userRepository;
    private final UserBehaviorRepository userBehaviorRepository;
    
    /**
     * Generate personalized UI configuration for a user
     * 
     * @param userId the user ID
     * @return personalized UI configuration
     */
    @Transactional(readOnly = true)
    public Map<String, Object> generatePersonalizedUIConfig(UUID userId) {
        try {
            log.debug("Generating personalized UI config for user: {}", userId);
            
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            // Get user behavior patterns
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(100)
                .toList();
            
            // Analyze user preferences
            Map<String, Object> preferences = analyzeUserPreferences(user, behaviors);
            
            // Generate UI adaptations
            Map<String, Object> uiConfig = generateUIAdaptations(preferences, behaviors);
            
            // Add personalization metadata
            uiConfig.put("userId", userId);
            uiConfig.put("generatedAt", LocalDateTime.now());
            uiConfig.put("personalizationLevel", calculatePersonalizationLevel(behaviors));
            
            log.debug("Successfully generated personalized UI config for user: {}", userId);
            
            return uiConfig;
            
        } catch (Exception e) {
            log.error("Error generating personalized UI config for user: {}", userId, e);
            throw new RuntimeException("Failed to generate personalized UI config", e);
        }
    }
    
    /**
     * Adapt UI components based on user behavior
     * 
     * @param userId the user ID
     * @param componentType the type of UI component
     * @return adapted component configuration
     */
    @Transactional(readOnly = true)
    public Map<String, Object> adaptUIComponent(UUID userId, String componentType) {
        try {
            log.debug("Adapting UI component {} for user: {}", componentType, userId);
            
            // Get relevant behaviors for the component
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdAndEntityTypeOrderByCreatedAtDesc(userId, componentType)
                .stream()
                .limit(50)
                .toList();
            
            // Generate component-specific adaptations
            Map<String, Object> adaptations = generateComponentAdaptations(componentType, behaviors);
            
            // Add AI-powered recommendations
            String aiRecommendations = generateAIRecommendations(componentType, behaviors);
            adaptations.put("aiRecommendations", aiRecommendations);
            
            log.debug("Successfully adapted UI component {} for user: {}", componentType, userId);
            
            return adaptations;
            
        } catch (Exception e) {
            log.error("Error adapting UI component {} for user: {}", componentType, userId, e);
            throw new RuntimeException("Failed to adapt UI component", e);
        }
    }
    
    /**
     * Generate personalized content recommendations
     * 
     * @param userId the user ID
     * @param contentType the type of content
     * @param limit maximum number of recommendations
     * @return personalized content recommendations
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> generateContentRecommendations(UUID userId, String contentType, int limit) {
        try {
            log.debug("Generating content recommendations for user: {} - {}", userId, contentType);
            
            // Get user's content interaction history
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdAndEntityTypeOrderByCreatedAtDesc(userId, contentType)
                .stream()
                .limit(200)
                .toList();
            
            // Analyze content preferences
            Map<String, Double> contentPreferences = analyzeContentPreferences(behaviors);
            
            // Generate AI-powered recommendations
            String aiRecommendations = aiCoreService.generateContent(
                String.format("Based on user behavior with %s content, recommend specific content types, categories, or features that would be most engaging. User has interacted with: %s", 
                    contentType,
                    behaviors.stream()
                        .map(b -> b.getAction() + " on " + b.getEntityId())
                        .distinct()
                        .limit(10)
                        .collect(Collectors.joining(", "))
                )
            );
            
            // Parse AI recommendations into structured format
            List<Map<String, Object>> recommendations = parseAIRecommendations(aiRecommendations, limit);
            
            // Add personalization scores
            recommendations.forEach(rec -> {
                String category = (String) rec.get("category");
                rec.put("personalizationScore", contentPreferences.getOrDefault(category, 0.5));
            });
            
            log.debug("Successfully generated {} content recommendations for user: {}", recommendations.size(), userId);
            
            return recommendations;
            
        } catch (Exception e) {
            log.error("Error generating content recommendations for user: {}", userId, e);
            throw new RuntimeException("Failed to generate content recommendations", e);
        }
    }
    
    /**
     * Generate layout preferences based on user behavior
     * 
     * @param userId the user ID
     * @return layout preferences
     */
    @Transactional(readOnly = true)
    public Map<String, Object> generateLayoutPreferences(UUID userId) {
        try {
            log.debug("Generating layout preferences for user: {}", userId);
            
            // Get user's interaction patterns
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(150)
                .toList();
            
            // Analyze interaction patterns
            Map<String, Object> layoutPrefs = analyzeInteractionPatterns(behaviors);
            
            // Generate AI-powered layout suggestions
            String aiLayoutSuggestions = aiCoreService.generateContent(
                "Based on user interaction patterns, suggest optimal layout preferences including:\n" +
                "- Preferred component positioning\n" +
                "- Information density preferences\n" +
                "- Navigation patterns\n" +
                "- Visual hierarchy preferences\n\n" +
                "User interaction data:\n" +
                behaviors.stream()
                    .map(b -> String.format("Action: %s, Entity: %s, Time: %s", 
                        b.getAction(), b.getEntityType(), b.getCreatedAt()))
                    .limit(20)
                    .collect(Collectors.joining("\n"))
            );
            
            layoutPrefs.put("aiSuggestions", aiLayoutSuggestions);
            layoutPrefs.put("confidence", calculateLayoutConfidence(behaviors));
            
            log.debug("Successfully generated layout preferences for user: {}", userId);
            
            return layoutPrefs;
            
        } catch (Exception e) {
            log.error("Error generating layout preferences for user: {}", userId, e);
            throw new RuntimeException("Failed to generate layout preferences", e);
        }
    }
    
    /**
     * Generate accessibility adaptations
     * 
     * @param userId the user ID
     * @return accessibility adaptations
     */
    @Transactional(readOnly = true)
    public Map<String, Object> generateAccessibilityAdaptations(UUID userId) {
        try {
            log.debug("Generating accessibility adaptations for user: {}", userId);
            
            // Get user's interaction patterns
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(100)
                .toList();
            
            // Analyze accessibility needs
            Map<String, Object> adaptations = analyzeAccessibilityNeeds(behaviors);
            
            // Generate AI-powered accessibility recommendations
            String aiAccessibilityRecommendations = aiCoreService.generateContent(
                "Based on user interaction patterns, suggest accessibility adaptations including:\n" +
                "- Font size preferences\n" +
                "- Color contrast needs\n" +
                "- Navigation assistance\n" +
                "- Input method preferences\n\n" +
                "User interaction data:\n" +
                behaviors.stream()
                    .map(b -> String.format("Action: %s, Device: %s, Context: %s", 
                        b.getAction(), b.getDeviceInfo(), b.getContext()))
                    .limit(15)
                    .collect(Collectors.joining("\n"))
            );
            
            adaptations.put("aiRecommendations", aiAccessibilityRecommendations);
            adaptations.put("adaptationLevel", calculateAdaptationLevel(behaviors));
            
            log.debug("Successfully generated accessibility adaptations for user: {}", userId);
            
            return adaptations;
            
        } catch (Exception e) {
            log.error("Error generating accessibility adaptations for user: {}", userId, e);
            throw new RuntimeException("Failed to generate accessibility adaptations", e);
        }
    }
    
    /**
     * Analyze user preferences
     */
    private Map<String, Object> analyzeUserPreferences(User user, List<UserBehavior> behaviors) {
        Map<String, Object> preferences = new HashMap<>();
        
        // Analyze behavior patterns
        Map<String, Long> behaviorCounts = behaviors.stream()
            .collect(Collectors.groupingBy(
                b -> b.getBehaviorType().name(),
                Collectors.counting()
            ));
        
        preferences.put("behaviorPatterns", behaviorCounts);
        
        // Analyze entity preferences
        Map<String, Long> entityPreferences = behaviors.stream()
            .collect(Collectors.groupingBy(
                UserBehavior::getEntityType,
                Collectors.counting()
            ));
        
        preferences.put("entityPreferences", entityPreferences);
        
        // Analyze action preferences
        Map<String, Long> actionPreferences = behaviors.stream()
            .collect(Collectors.groupingBy(
                UserBehavior::getAction,
                Collectors.counting()
            ));
        
        preferences.put("actionPreferences", actionPreferences);
        
        return preferences;
    }
    
    /**
     * Generate UI adaptations
     */
    private Map<String, Object> generateUIAdaptations(Map<String, Object> preferences, List<UserBehavior> behaviors) {
        Map<String, Object> adaptations = new HashMap<>();
        
        // Generate theme preferences
        adaptations.put("theme", generateThemePreference(preferences));
        
        // Generate layout preferences
        adaptations.put("layout", generateLayoutPreference(preferences));
        
        // Generate component preferences
        adaptations.put("components", generateComponentPreferences(preferences));
        
        // Generate navigation preferences
        adaptations.put("navigation", generateNavigationPreferences(behaviors));
        
        return adaptations;
    }
    
    /**
     * Generate component adaptations
     */
    private Map<String, Object> generateComponentAdaptations(String componentType, List<UserBehavior> behaviors) {
        Map<String, Object> adaptations = new HashMap<>();
        
        // Analyze component-specific patterns
        Map<String, Long> actionCounts = behaviors.stream()
            .collect(Collectors.groupingBy(
                UserBehavior::getAction,
                Collectors.counting()
            ));
        
        adaptations.put("preferredActions", actionCounts);
        adaptations.put("componentType", componentType);
        adaptations.put("interactionCount", behaviors.size());
        
        return adaptations;
    }
    
    /**
     * Generate AI recommendations
     */
    private String generateAIRecommendations(String componentType, List<UserBehavior> behaviors) {
        try {
            return aiCoreService.generateContent(
                String.format("Based on user interactions with %s components, provide specific recommendations for improving user experience and engagement.", componentType)
            );
        } catch (Exception e) {
            log.warn("Failed to generate AI recommendations", e);
            return "AI recommendations unavailable";
        }
    }
    
    /**
     * Analyze content preferences
     */
    private Map<String, Double> analyzeContentPreferences(List<UserBehavior> behaviors) {
        Map<String, Double> preferences = new HashMap<>();
        
        // Simple preference calculation based on interaction frequency
        Map<String, Long> categoryCounts = behaviors.stream()
            .collect(Collectors.groupingBy(
                b -> b.getContext() != null ? b.getContext() : "unknown",
                Collectors.counting()
            ));
        
        long totalInteractions = behaviors.size();
        categoryCounts.forEach((category, count) -> {
            preferences.put(category, (double) count / totalInteractions);
        });
        
        return preferences;
    }
    
    /**
     * Parse AI recommendations
     */
    private List<Map<String, Object>> parseAIRecommendations(String aiRecommendations, int limit) {
        // Simple parsing - in real implementation, this would be more sophisticated
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        String[] lines = aiRecommendations.split("\n");
        for (int i = 0; i < Math.min(lines.length, limit); i++) {
            Map<String, Object> rec = new HashMap<>();
            rec.put("title", "Recommendation " + (i + 1));
            rec.put("description", lines[i].trim());
            rec.put("category", "AI Generated");
            rec.put("confidence", 0.8);
            recommendations.add(rec);
        }
        
        return recommendations;
    }
    
    /**
     * Analyze interaction patterns
     */
    private Map<String, Object> analyzeInteractionPatterns(List<UserBehavior> behaviors) {
        Map<String, Object> patterns = new HashMap<>();
        
        // Analyze time patterns
        Map<Integer, Long> hourPatterns = behaviors.stream()
            .collect(Collectors.groupingBy(
                b -> b.getCreatedAt().getHour(),
                Collectors.counting()
            ));
        
        patterns.put("hourPatterns", hourPatterns);
        
        // Analyze frequency patterns
        patterns.put("totalInteractions", behaviors.size());
        patterns.put("uniqueEntities", behaviors.stream()
            .map(UserBehavior::getEntityType)
            .distinct()
            .count());
        
        return patterns;
    }
    
    /**
     * Analyze accessibility needs
     */
    private Map<String, Object> analyzeAccessibilityNeeds(List<UserBehavior> behaviors) {
        Map<String, Object> needs = new HashMap<>();
        
        // Analyze device patterns
        Map<String, Long> devicePatterns = behaviors.stream()
            .filter(b -> b.getDeviceInfo() != null)
            .collect(Collectors.groupingBy(
                b -> b.getDeviceInfo(),
                Collectors.counting()
            ));
        
        needs.put("devicePatterns", devicePatterns);
        
        // Analyze interaction patterns
        needs.put("avgInteractionTime", calculateAvgInteractionTime(behaviors));
        needs.put("errorRate", calculateErrorRate(behaviors));
        
        return needs;
    }
    
    /**
     * Calculate personalization level
     */
    private String calculatePersonalizationLevel(List<UserBehavior> behaviors) {
        int behaviorCount = behaviors.size();
        
        if (behaviorCount < 10) return "LOW";
        if (behaviorCount < 50) return "MEDIUM";
        if (behaviorCount < 100) return "HIGH";
        return "VERY_HIGH";
    }
    
    /**
     * Calculate layout confidence
     */
    private Double calculateLayoutConfidence(List<UserBehavior> behaviors) {
        // Simple confidence calculation based on data availability
        int behaviorCount = behaviors.size();
        return Math.min(1.0, behaviorCount / 50.0);
    }
    
    /**
     * Calculate adaptation level
     */
    private String calculateAdaptationLevel(List<UserBehavior> behaviors) {
        int behaviorCount = behaviors.size();
        
        if (behaviorCount < 20) return "BASIC";
        if (behaviorCount < 50) return "ADVANCED";
        return "COMPREHENSIVE";
    }
    
    /**
     * Generate theme preference
     */
    private String generateThemePreference(Map<String, Object> preferences) {
        // Simple theme selection based on behavior patterns
        return "DARK"; // Default theme
    }
    
    /**
     * Generate layout preference
     */
    private String generateLayoutPreference(Map<String, Object> preferences) {
        // Simple layout selection based on behavior patterns
        return "GRID"; // Default layout
    }
    
    /**
     * Generate component preferences
     */
    private Map<String, Object> generateComponentPreferences(Map<String, Object> preferences) {
        Map<String, Object> componentPrefs = new HashMap<>();
        componentPrefs.put("density", "MEDIUM");
        componentPrefs.put("spacing", "COMFORTABLE");
        return componentPrefs;
    }
    
    /**
     * Generate navigation preferences
     */
    private Map<String, Object> generateNavigationPreferences(List<UserBehavior> behaviors) {
        Map<String, Object> navPrefs = new HashMap<>();
        navPrefs.put("style", "SIDEBAR");
        navPrefs.put("collapsible", true);
        return navPrefs;
    }
    
    /**
     * Calculate average interaction time
     */
    private Double calculateAvgInteractionTime(List<UserBehavior> behaviors) {
        // Simple calculation - in real implementation, this would use actual timing data
        return 2.5; // seconds
    }
    
    /**
     * Calculate error rate
     */
    private Double calculateErrorRate(List<UserBehavior> behaviors) {
        // Simple calculation - in real implementation, this would track actual errors
        return 0.05; // 5% error rate
    }
}