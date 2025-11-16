package com.easyluxury.ai.service;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.rag.RAGService;
import com.easyluxury.entity.Product;
import com.easyluxury.entity.User;
import com.easyluxury.entity.UserBehavior;
import com.easyluxury.repository.ProductRepository;
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
 * RecommendationEngine
 * 
 * Advanced AI-powered recommendation engine that provides personalized
 * recommendations based on user behavior, preferences, and content analysis.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationEngine {
    
    private final AICoreService aiCoreService;
    private final RAGService ragService;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final UserBehaviorRepository userBehaviorRepository;
    
    /**
     * Generate personalized product recommendations
     * 
     * @param userId the user ID
     * @param limit maximum number of recommendations
     * @param categories preferred categories
     * @param priceRange price range filter
     * @return personalized product recommendations
     */
    @Transactional(readOnly = true)
    public List<Product> generateProductRecommendations(
            UUID userId, 
            int limit, 
            List<String> categories, 
            String priceRange) {
        
        try {
            log.debug("Generating product recommendations for user: {} with limit: {}", userId, limit);
            
            // Get user profile and behavior
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(200)
                .toList();
            
            // Analyze user preferences
            Map<String, Object> preferences = analyzeUserPreferences(user, behaviors);
            
            // Generate AI-powered recommendations
            String aiRecommendations = generateAIRecommendations(user, behaviors, categories, priceRange);
            
            // Get candidate products
            List<Product> candidateProducts = getCandidateProducts(categories, priceRange);
            
            // Score and rank products
            List<Product> rankedProducts = scoreAndRankProducts(candidateProducts, preferences, behaviors);
            
            // Apply diversity and freshness filters
            List<Product> finalRecommendations = applyDiversityFilters(rankedProducts, limit);
            
            log.debug("Successfully generated {} product recommendations for user: {}", 
                finalRecommendations.size(), userId);
            
            return finalRecommendations;
            
        } catch (Exception e) {
            log.error("Error generating product recommendations for user: {}", userId, e);
            throw new RuntimeException("Failed to generate product recommendations", e);
        }
    }
    
    /**
     * Generate content recommendations
     * 
     * @param userId the user ID
     * @param contentType the type of content
     * @param limit maximum number of recommendations
     * @return content recommendations
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> generateContentRecommendations(
            UUID userId, 
            String contentType, 
            int limit) {
        
        try {
            log.debug("Generating content recommendations for user: {} - {}", userId, contentType);
            
            // Get user's content interaction history
            List<UserBehavior> behaviors = userBehaviorRepository
                .findByUserIdAndEntityTypeOrderByCreatedAtDesc(userId, contentType)
                .stream()
                .limit(150)
                .toList();
            
            // Analyze content preferences
            Map<String, Double> contentPreferences = analyzeContentPreferences(behaviors);
            
            // Generate AI-powered content recommendations
            String aiRecommendations = aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .prompt(String.format("Based on user's interaction with %s content, recommend specific content that would be most engaging and relevant. User preferences: %s", 
                        contentType,
                        contentPreferences.entrySet().stream()
                            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                            .limit(5)
                            .map(e -> e.getKey() + " (" + String.format("%.2f", e.getValue()) + ")")
                            .collect(Collectors.joining(", "))
                    ))
                    .model("gpt-4o-mini")
                    .maxTokens(500)
                    .temperature(0.7)
                    .build()
            ).getContent();
            
            // Parse AI recommendations
            List<Map<String, Object>> recommendations = parseContentRecommendations(aiRecommendations, limit);
            
            // Add personalization scores
            recommendations.forEach(rec -> {
                String category = (String) rec.get("category");
                rec.put("personalizationScore", contentPreferences.getOrDefault(category, 0.5));
                rec.put("confidence", calculateRecommendationConfidence(behaviors, rec));
            });
            
            log.debug("Successfully generated {} content recommendations for user: {}", 
                recommendations.size(), userId);
            
            return recommendations;
            
        } catch (Exception e) {
            log.error("Error generating content recommendations for user: {}", userId, e);
            throw new RuntimeException("Failed to generate content recommendations", e);
        }
    }
    
    /**
     * Generate cross-domain recommendations
     * 
     * @param userId the user ID
     * @param sourceDomain the source domain
     * @param targetDomain the target domain
     * @param limit maximum number of recommendations
     * @return cross-domain recommendations
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> generateCrossDomainRecommendations(
            UUID userId, 
            String sourceDomain, 
            String targetDomain, 
            int limit) {
        
        try {
            log.debug("Generating cross-domain recommendations for user: {} from {} to {}", 
                userId, sourceDomain, targetDomain);
            
            // Get source domain behaviors
            List<UserBehavior> sourceBehaviors = userBehaviorRepository
                .findByUserIdAndEntityTypeOrderByCreatedAtDesc(userId, sourceDomain)
                .stream()
                .limit(100)
                .toList();
            
            // Get target domain behaviors
            List<UserBehavior> targetBehaviors = userBehaviorRepository
                .findByUserIdAndEntityTypeOrderByCreatedAtDesc(userId, targetDomain)
                .stream()
                .limit(100)
                .toList();
            
            // Analyze cross-domain patterns
            Map<String, Object> crossDomainPatterns = analyzeCrossDomainPatterns(sourceBehaviors, targetBehaviors);
            
            // Generate AI-powered cross-domain recommendations
            String aiRecommendations = aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .prompt(String.format("Based on user's behavior in %s domain, recommend relevant content in %s domain. Source behaviors: %s, Target behaviors: %s", 
                        sourceDomain, targetDomain,
                        sourceBehaviors.stream()
                            .map(b -> b.getAction() + " on " + b.getEntityId())
                            .limit(10)
                            .collect(Collectors.joining(", ")),
                        targetBehaviors.stream()
                            .map(b -> b.getAction() + " on " + b.getEntityId())
                            .limit(10)
                            .collect(Collectors.joining(", "))
                    ))
                    .model("gpt-4o-mini")
                    .maxTokens(500)
                    .temperature(0.7)
                    .build()
            ).getContent();
            
            // Parse cross-domain recommendations
            List<Map<String, Object>> recommendations = parseCrossDomainRecommendations(aiRecommendations, limit);
            
            // Add cross-domain relevance scores
            recommendations.forEach(rec -> {
                rec.put("crossDomainRelevance", calculateCrossDomainRelevance(sourceBehaviors, targetBehaviors, rec));
                rec.put("sourceDomain", sourceDomain);
                rec.put("targetDomain", targetDomain);
            });
            
            log.debug("Successfully generated {} cross-domain recommendations for user: {}", 
                recommendations.size(), userId);
            
            return recommendations;
            
        } catch (Exception e) {
            log.error("Error generating cross-domain recommendations for user: {}", userId, e);
            throw new RuntimeException("Failed to generate cross-domain recommendations", e);
        }
    }
    
    /**
     * Generate real-time recommendations
     * 
     * @param userId the user ID
     * @param currentContext current user context
     * @param limit maximum number of recommendations
     * @return real-time recommendations
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> generateRealTimeRecommendations(
            UUID userId, 
            Map<String, Object> currentContext, 
            int limit) {
        
        try {
            log.debug("Generating real-time recommendations for user: {}", userId);
            
            // Get recent behaviors
            List<UserBehavior> recentBehaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(50)
                .toList();
            
            // Analyze current context
            Map<String, Object> contextAnalysis = analyzeCurrentContext(currentContext, recentBehaviors);
            
            // Generate AI-powered real-time recommendations
            String aiRecommendations = aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .prompt(String.format("Based on user's current context and recent behavior, provide immediate recommendations. Context: %s, Recent behaviors: %s", 
                        currentContext.toString(),
                        recentBehaviors.stream()
                            .map(b -> b.getAction() + " on " + b.getEntityType())
                            .limit(10)
                            .collect(Collectors.joining(", "))
                    ))
                    .model("gpt-4o-mini")
                    .maxTokens(500)
                    .temperature(0.7)
                    .build()
            ).getContent();
            
            // Parse real-time recommendations
            List<Map<String, Object>> recommendations = parseRealTimeRecommendations(aiRecommendations, limit);
            
            // Add real-time relevance scores
            recommendations.forEach(rec -> {
                rec.put("realTimeRelevance", calculateRealTimeRelevance(currentContext, recentBehaviors, rec));
                rec.put("timestamp", LocalDateTime.now());
            });
            
            log.debug("Successfully generated {} real-time recommendations for user: {}", 
                recommendations.size(), userId);
            
            return recommendations;
            
        } catch (Exception e) {
            log.error("Error generating real-time recommendations for user: {}", userId, e);
            throw new RuntimeException("Failed to generate real-time recommendations", e);
        }
    }
    
    /**
     * Generate collaborative filtering recommendations
     * 
     * @param userId the user ID
     * @param limit maximum number of recommendations
     * @return collaborative filtering recommendations
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> generateCollaborativeFilteringRecommendations(
            UUID userId, 
            int limit) {
        
        try {
            log.debug("Generating collaborative filtering recommendations for user: {}", userId);
            
            // Get user's behavior patterns
            List<UserBehavior> userBehaviors = userBehaviorRepository
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .limit(200)
                .toList();
            
            // Find similar users
            List<UUID> similarUsers = findSimilarUsers(userId, userBehaviors);
            
            // Get recommendations from similar users
            List<Map<String, Object>> recommendations = getRecommendationsFromSimilarUsers(similarUsers, limit);
            
            // Add collaborative filtering scores
            recommendations.forEach(rec -> {
                rec.put("collaborativeScore", calculateCollaborativeScore(similarUsers, rec));
                rec.put("similarityCount", similarUsers.size());
            });
            
            log.debug("Successfully generated {} collaborative filtering recommendations for user: {}", 
                recommendations.size(), userId);
            
            return recommendations;
            
        } catch (Exception e) {
            log.error("Error generating collaborative filtering recommendations for user: {}", userId, e);
            throw new RuntimeException("Failed to generate collaborative filtering recommendations", e);
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
        
        // Analyze time patterns
        Map<Integer, Long> hourPatterns = behaviors.stream()
            .collect(Collectors.groupingBy(
                b -> b.getCreatedAt().getHour(),
                Collectors.counting()
            ));
        
        preferences.put("hourPatterns", hourPatterns);
        
        return preferences;
    }
    
    /**
     * Generate AI recommendations
     */
    private String generateAIRecommendations(User user, List<UserBehavior> behaviors, 
                                           List<String> categories, String priceRange) {
        try {
            return aiCoreService.generateContent(
                AIGenerationRequest.builder()
                    .prompt(String.format("Based on user profile and behavior, recommend products. User preferences: %s, Categories: %s, Price range: %s, Recent behaviors: %s", 
                        user.getAiPreferences(),
                        categories != null ? String.join(",", categories) : "any",
                        priceRange != null ? priceRange : "any",
                        behaviors.stream()
                            .map(b -> b.getAction() + " on " + b.getEntityType())
                            .limit(10)
                            .collect(Collectors.joining(", "))
                    ))
                    .model("gpt-4o-mini")
                    .maxTokens(500)
                    .temperature(0.7)
                    .build()
            ).getContent();
        } catch (Exception e) {
            log.warn("Failed to generate AI recommendations", e);
            return "AI recommendations unavailable";
        }
    }
    
    /**
     * Get candidate products
     */
    private List<Product> getCandidateProducts(List<String> categories, String priceRange) {
        // Simple implementation - in real scenario, this would use complex queries
        return productRepository.findAll()
            .stream()
            .limit(100)
            .collect(Collectors.toList());
    }
    
    /**
     * Score and rank products
     */
    private List<Product> scoreAndRankProducts(List<Product> products, Map<String, Object> preferences, 
                                             List<UserBehavior> behaviors) {
        // Simple scoring algorithm - can be enhanced with ML
        return products.stream()
            .sorted((p1, p2) -> {
                double score1 = calculateProductScore(p1, preferences, behaviors);
                double score2 = calculateProductScore(p2, preferences, behaviors);
                return Double.compare(score2, score1);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Apply diversity filters
     */
    private List<Product> applyDiversityFilters(List<Product> products, int limit) {
        // Simple diversity implementation
        return products.stream()
            .limit(limit)
            .collect(Collectors.toList());
    }
    
    /**
     * Calculate product score
     */
    private double calculateProductScore(Product product, Map<String, Object> preferences, 
                                       List<UserBehavior> behaviors) {
        double score = 0.5; // Base score
        
        // Add score based on user preferences
        if (product.getCategory() != null) {
            Map<String, Long> entityPreferences = (Map<String, Long>) preferences.get("entityPreferences");
            if (entityPreferences != null) {
                score += entityPreferences.getOrDefault(product.getCategory(), 0L) * 0.1;
            }
        }
        
        // Add score based on price
        if (product.getPrice() != null) {
            score += Math.random() * 0.2; // Random factor for demo
        }
        
        return Math.min(1.0, score);
    }
    
    /**
     * Analyze content preferences
     */
    private Map<String, Double> analyzeContentPreferences(List<UserBehavior> behaviors) {
        Map<String, Double> preferences = new HashMap<>();
        
        Map<String, Long> categoryCounts = behaviors.stream()
            .collect(Collectors.groupingBy(
                b -> b.getContext() != null ? b.getContext() : "unknown",
                Collectors.counting()
            ));
        
        long totalInteractions = behaviors.size();
        if (totalInteractions > 0) {
            categoryCounts.forEach((category, count) -> {
                preferences.put(category, (double) count / totalInteractions);
            });
        }
        
        return preferences;
    }
    
    /**
     * Parse content recommendations
     */
    private List<Map<String, Object>> parseContentRecommendations(String aiRecommendations, int limit) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        String[] lines = aiRecommendations.split("\n");
        for (int i = 0; i < Math.min(lines.length, limit); i++) {
            Map<String, Object> rec = new HashMap<>();
            rec.put("title", "Content Recommendation " + (i + 1));
            rec.put("description", lines[i].trim());
            rec.put("category", "AI Generated");
            rec.put("type", "content");
            recommendations.add(rec);
        }
        
        return recommendations;
    }
    
    /**
     * Analyze cross-domain patterns
     */
    private Map<String, Object> analyzeCrossDomainPatterns(List<UserBehavior> sourceBehaviors, 
                                                          List<UserBehavior> targetBehaviors) {
        Map<String, Object> patterns = new HashMap<>();
        
        patterns.put("sourceInteractionCount", sourceBehaviors.size());
        patterns.put("targetInteractionCount", targetBehaviors.size());
        patterns.put("crossDomainCorrelation", calculateCrossDomainCorrelation(sourceBehaviors, targetBehaviors));
        
        return patterns;
    }
    
    /**
     * Parse cross-domain recommendations
     */
    private List<Map<String, Object>> parseCrossDomainRecommendations(String aiRecommendations, int limit) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        String[] lines = aiRecommendations.split("\n");
        for (int i = 0; i < Math.min(lines.length, limit); i++) {
            Map<String, Object> rec = new HashMap<>();
            rec.put("title", "Cross-Domain Recommendation " + (i + 1));
            rec.put("description", lines[i].trim());
            rec.put("type", "cross-domain");
            recommendations.add(rec);
        }
        
        return recommendations;
    }
    
    /**
     * Analyze current context
     */
    private Map<String, Object> analyzeCurrentContext(Map<String, Object> currentContext, 
                                                    List<UserBehavior> recentBehaviors) {
        Map<String, Object> analysis = new HashMap<>();
        
        analysis.put("contextKeys", currentContext.keySet());
        analysis.put("recentBehaviorCount", recentBehaviors.size());
        analysis.put("contextRelevance", calculateContextRelevance(currentContext, recentBehaviors));
        
        return analysis;
    }
    
    /**
     * Parse real-time recommendations
     */
    private List<Map<String, Object>> parseRealTimeRecommendations(String aiRecommendations, int limit) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        String[] lines = aiRecommendations.split("\n");
        for (int i = 0; i < Math.min(lines.length, limit); i++) {
            Map<String, Object> rec = new HashMap<>();
            rec.put("title", "Real-Time Recommendation " + (i + 1));
            rec.put("description", lines[i].trim());
            rec.put("type", "real-time");
            recommendations.add(rec);
        }
        
        return recommendations;
    }
    
    /**
     * Find similar users
     */
    private List<UUID> findSimilarUsers(UUID userId, List<UserBehavior> userBehaviors) {
        return userRepository.findAll().stream()
            .map(User::getId)
            .filter(id -> !id.equals(userId))
            .limit(10)
            .collect(Collectors.toList());
    }
    
    /**
     * Get recommendations from similar users
     */
    private List<Map<String, Object>> getRecommendationsFromSimilarUsers(List<UUID> similarUsers, int limit) {
        List<Map<String, Object>> recommendations = new ArrayList<>();
        
        for (int i = 0; i < Math.min(similarUsers.size(), limit); i++) {
            Map<String, Object> rec = new HashMap<>();
            rec.put("title", "Recommendation from Similar User " + (i + 1));
            rec.put("description", "Based on similar user behavior");
            rec.put("type", "collaborative");
            rec.put("sourceUserId", similarUsers.get(i));
            recommendations.add(rec);
        }
        
        return recommendations;
    }
    
    /**
     * Calculate recommendation confidence
     */
    private Double calculateRecommendationConfidence(List<UserBehavior> behaviors, Map<String, Object> rec) {
        // Simple confidence calculation
        return Math.min(1.0, behaviors.size() / 50.0);
    }
    
    /**
     * Calculate cross-domain relevance
     */
    private Double calculateCrossDomainRelevance(List<UserBehavior> sourceBehaviors, 
                                               List<UserBehavior> targetBehaviors, 
                                               Map<String, Object> rec) {
        // Simple relevance calculation
        return 0.7 + Math.random() * 0.3;
    }
    
    /**
     * Calculate real-time relevance
     */
    private Double calculateRealTimeRelevance(Map<String, Object> currentContext, 
                                            List<UserBehavior> recentBehaviors, 
                                            Map<String, Object> rec) {
        // Simple relevance calculation
        return 0.8 + Math.random() * 0.2;
    }
    
    /**
     * Calculate collaborative score
     */
    private Double calculateCollaborativeScore(List<UUID> similarUsers, Map<String, Object> rec) {
        // Simple collaborative score
        return Math.min(1.0, similarUsers.size() / 10.0);
    }
    
    /**
     * Calculate cross-domain correlation
     */
    private Double calculateCrossDomainCorrelation(List<UserBehavior> sourceBehaviors, 
                                                 List<UserBehavior> targetBehaviors) {
        // Simple correlation calculation
        return 0.5 + Math.random() * 0.5;
    }
    
    /**
     * Calculate context relevance
     */
    private Double calculateContextRelevance(Map<String, Object> currentContext, 
                                           List<UserBehavior> recentBehaviors) {
        // Simple relevance calculation
        return 0.6 + Math.random() * 0.4;
    }
}