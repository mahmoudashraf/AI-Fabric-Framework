package com.ai.infrastructure.filter;

import com.ai.infrastructure.dto.AIContentFilterRequest;
import com.ai.infrastructure.dto.AIContentFilterResponse;
import com.ai.infrastructure.core.AICoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI Content Filter Service for content moderation and filtering
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AIContentFilterService {

    private final AICoreService aiCoreService;
    private final Map<String, List<String>> blockedContent = new ConcurrentHashMap<>();
    private final Map<String, List<String>> allowedContent = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> filterSettings = new ConcurrentHashMap<>();

    /**
     * Filter content based on policies and rules
     */
    public AIContentFilterResponse filterContent(AIContentFilterRequest request) {
        log.info("Filtering content for user: {}", request.getUserId());
        
        try {
            long startTime = System.currentTimeMillis();
            
            // Analyze content for violations
            List<String> violations = analyzeContentViolations(request);
            
            // Check content against blocked lists
            boolean isBlocked = checkBlockedContent(request.getContent());
            
            // Check content against allowed lists
            boolean isAllowed = checkAllowedContent(request.getContent());
            
            // Determine if content should be filtered
            boolean shouldFilter = shouldFilterContent(violations, isBlocked, isAllowed, request);
            
            // Apply content sanitization if needed
            String sanitizedContent = applyContentSanitization(request.getContent(), violations);
            
            // Generate filter recommendations
            List<String> recommendations = generateFilterRecommendations(violations, shouldFilter);
            
            // Calculate content score
            double contentScore = calculateContentScore(request.getContent(), violations);
            
            long processingTime = System.currentTimeMillis() - startTime;
            
            return AIContentFilterResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .violations(violations)
                .isBlocked(isBlocked)
                .isAllowed(isAllowed)
                .shouldFilter(shouldFilter)
                .sanitizedContent(sanitizedContent)
                .contentScore(contentScore)
                .recommendations(recommendations)
                .processingTimeMs(processingTime)
                .timestamp(LocalDateTime.now())
                .success(true)
                .build();
                
        } catch (Exception e) {
            log.error("Error filtering content", e);
            return AIContentFilterResponse.builder()
                .requestId(request.getRequestId())
                .userId(request.getUserId())
                .shouldFilter(true) // Default to filtering on error
                .success(false)
                .errorMessage(e.getMessage())
                .build();
        }
    }

    /**
     * Analyze content for violations using AI
     */
    private List<String> analyzeContentViolations(AIContentFilterRequest request) {
        List<String> violations = new ArrayList<>();
        
        try {
            String prompt = String.format(
                "Analyze this content for violations. Check for: " +
                "hate speech, harassment, violence, explicit content, " +
                "spam, misinformation, or other policy violations.\n\n" +
                "Content: %s\n\n" +
                "Return only the violation types found, one per line, or 'NONE' if no violations.",
                request.getContent()
            );
            
            String response = aiCoreService.generateText(prompt);
            violations = Arrays.stream(response.split("\n"))
                .map(String::trim)
                .filter(violation -> !violation.isEmpty() && !violation.equals("NONE"))
                .collect(Collectors.toList());
                
        } catch (Exception e) {
            log.warn("AI content analysis failed, using rule-based detection", e);
            violations = detectRuleBasedViolations(request.getContent());
        }
        
        return violations;
    }

    /**
     * Detect violations using rule-based approach
     */
    private List<String> detectRuleBasedViolations(String content) {
        List<String> violations = new ArrayList<>();
        
        if (content == null) return violations;
        
        String lowerContent = content.toLowerCase();
        
        // Check for hate speech
        if (containsHateSpeech(lowerContent)) {
            violations.add("HATE_SPEECH");
        }
        
        // Check for harassment
        if (containsHarassment(lowerContent)) {
            violations.add("HARASSMENT");
        }
        
        // Check for violence
        if (containsViolence(lowerContent)) {
            violations.add("VIOLENCE");
        }
        
        // Check for explicit content
        if (containsExplicitContent(lowerContent)) {
            violations.add("EXPLICIT_CONTENT");
        }
        
        // Check for spam
        if (containsSpam(lowerContent)) {
            violations.add("SPAM");
        }
        
        // Check for misinformation
        if (containsMisinformation(lowerContent)) {
            violations.add("MISINFORMATION");
        }
        
        return violations;
    }

    /**
     * Check for hate speech patterns
     */
    private boolean containsHateSpeech(String content) {
        String[] hateSpeechPatterns = {
            "hate", "racist", "discrimination", "prejudice", "bigot",
            "slur", "offensive", "derogatory"
        };
        
        return Arrays.stream(hateSpeechPatterns)
            .anyMatch(content::contains);
    }

    /**
     * Check for harassment patterns
     */
    private boolean containsHarassment(String content) {
        String[] harassmentPatterns = {
            "bully", "threat", "intimidate", "harass", "stalk",
            "abuse", "attack", "target"
        };
        
        return Arrays.stream(harassmentPatterns)
            .anyMatch(content::contains);
    }

    /**
     * Check for violence patterns
     */
    private boolean containsViolence(String content) {
        String[] violencePatterns = {
            "kill", "murder", "violence", "attack", "harm",
            "weapon", "gun", "knife", "bomb"
        };
        
        return Arrays.stream(violencePatterns)
            .anyMatch(content::contains);
    }

    /**
     * Check for explicit content patterns
     */
    private boolean containsExplicitContent(String content) {
        String[] explicitPatterns = {
            "explicit", "adult", "nsfw", "sexual", "porn"
        };
        
        return Arrays.stream(explicitPatterns)
            .anyMatch(content::contains);
    }

    /**
     * Check for spam patterns
     */
    private boolean containsSpam(String content) {
        // Check for excessive repetition
        if (content.length() > 1000) {
            String[] words = content.split("\\s+");
            Map<String, Long> wordCounts = Arrays.stream(words)
                .collect(Collectors.groupingBy(word -> word, Collectors.counting()));
            
            long maxCount = wordCounts.values().stream()
                .mapToLong(Long::longValue)
                .max()
                .orElse(0);
            
            if (maxCount > words.length * 0.3) {
                return true; // Same word appears too frequently
            }
        }
        
        // Check for spam keywords
        String[] spamPatterns = {
            "click here", "free money", "win now", "act now",
            "limited time", "guaranteed", "no risk"
        };
        
        return Arrays.stream(spamPatterns)
            .anyMatch(content::contains);
    }

    /**
     * Check for misinformation patterns
     */
    private boolean containsMisinformation(String content) {
        String[] misinformationPatterns = {
            "fake news", "conspiracy", "hoax", "false claim",
            "misleading", "deceptive", "fraud"
        };
        
        return Arrays.stream(misinformationPatterns)
            .anyMatch(content::contains);
    }

    /**
     * Check if content is in blocked list
     */
    private boolean checkBlockedContent(String content) {
        if (content == null) return false;
        
        String lowerContent = content.toLowerCase();
        
        // Check against global blocked content
        return blockedContent.values().stream()
            .flatMap(List::stream)
            .anyMatch(blocked -> lowerContent.contains(blocked.toLowerCase()));
    }

    /**
     * Check if content is in allowed list
     */
    private boolean checkAllowedContent(String content) {
        if (content == null) return false;
        
        String lowerContent = content.toLowerCase();
        
        // Check against global allowed content
        return allowedContent.values().stream()
            .flatMap(List::stream)
            .anyMatch(allowed -> lowerContent.contains(allowed.toLowerCase()));
    }

    /**
     * Determine if content should be filtered
     */
    private boolean shouldFilterContent(List<String> violations, boolean isBlocked, 
                                      boolean isAllowed, AIContentFilterRequest request) {
        // Always filter if explicitly blocked
        if (isBlocked) {
            return true;
        }
        
        // Never filter if explicitly allowed
        if (isAllowed) {
            return false;
        }
        
        // Filter based on violations
        if (!violations.isEmpty()) {
            // Check if violations exceed threshold
            int violationCount = violations.size();
            int maxViolations = request.getMaxViolations() != null ? request.getMaxViolations() : 3;
            
            if (violationCount > maxViolations) {
                return true;
            }
            
            // Check for critical violations
            List<String> criticalViolations = Arrays.asList("HATE_SPEECH", "VIOLENCE", "HARASSMENT");
            if (violations.stream().anyMatch(criticalViolations::contains)) {
                return true;
            }
        }
        
        // Check content score threshold
        double contentScore = calculateContentScore(request.getContent(), violations);
        double minScore = request.getMinContentScore() != null ? request.getMinContentScore() : 0.5;
        
        if (contentScore < minScore) {
            return true;
        }
        
        return false;
    }

    /**
     * Apply content sanitization
     */
    private String applyContentSanitization(String content, List<String> violations) {
        if (content == null || violations.isEmpty()) {
            return content;
        }
        
        String sanitized = content;
        
        // Remove or replace offensive content
        for (String violation : violations) {
            switch (violation) {
                case "HATE_SPEECH":
                    sanitized = sanitizeHateSpeech(sanitized);
                    break;
                case "HARASSMENT":
                    sanitized = sanitizeHarassment(sanitized);
                    break;
                case "VIOLENCE":
                    sanitized = sanitizeViolence(sanitized);
                    break;
                case "EXPLICIT_CONTENT":
                    sanitized = sanitizeExplicitContent(sanitized);
                    break;
                case "SPAM":
                    sanitized = sanitizeSpam(sanitized);
                    break;
            }
        }
        
        return sanitized;
    }

    /**
     * Sanitize hate speech
     */
    private String sanitizeHateSpeech(String content) {
        // Replace offensive words with [REDACTED]
        String[] offensiveWords = {"hate", "racist", "discrimination"};
        for (String word : offensiveWords) {
            content = content.replaceAll("(?i)\\b" + word + "\\b", "[REDACTED]");
        }
        return content;
    }

    /**
     * Sanitize harassment
     */
    private String sanitizeHarassment(String content) {
        // Replace harassment-related words
        String[] harassmentWords = {"bully", "threat", "harass"};
        for (String word : harassmentWords) {
            content = content.replaceAll("(?i)\\b" + word + "\\b", "[REDACTED]");
        }
        return content;
    }

    /**
     * Sanitize violence
     */
    private String sanitizeViolence(String content) {
        // Replace violence-related words
        String[] violenceWords = {"kill", "murder", "violence", "attack"};
        for (String word : violenceWords) {
            content = content.replaceAll("(?i)\\b" + word + "\\b", "[REDACTED]");
        }
        return content;
    }

    /**
     * Sanitize explicit content
     */
    private String sanitizeExplicitContent(String content) {
        // Replace explicit content markers
        String[] explicitWords = {"explicit", "adult", "nsfw"};
        for (String word : explicitWords) {
            content = content.replaceAll("(?i)\\b" + word + "\\b", "[REDACTED]");
        }
        return content;
    }

    /**
     * Sanitize spam
     */
    private String sanitizeSpam(String content) {
        // Remove excessive repetition
        String[] words = content.split("\\s+");
        Map<String, Long> wordCounts = Arrays.stream(words)
            .collect(Collectors.groupingBy(word -> word, Collectors.counting()));
        
        // Replace frequently repeated words
        for (Map.Entry<String, Long> entry : wordCounts.entrySet()) {
            if (entry.getValue() > words.length * 0.3) {
                content = content.replaceAll("\\b" + entry.getKey() + "\\b", "[REDACTED]");
            }
        }
        
        return content;
    }

    /**
     * Generate filter recommendations
     */
    private List<String> generateFilterRecommendations(List<String> violations, boolean shouldFilter) {
        List<String> recommendations = new ArrayList<>();
        
        if (shouldFilter) {
            recommendations.add("Content has been filtered due to policy violations");
        }
        
        for (String violation : violations) {
            switch (violation) {
                case "HATE_SPEECH":
                    recommendations.add("Avoid using language that promotes hatred or discrimination");
                    break;
                case "HARASSMENT":
                    recommendations.add("Ensure content is respectful and does not harass others");
                    break;
                case "VIOLENCE":
                    recommendations.add("Avoid content that promotes or glorifies violence");
                    break;
                case "EXPLICIT_CONTENT":
                    recommendations.add("Ensure content is appropriate for all audiences");
                    break;
                case "SPAM":
                    recommendations.add("Avoid repetitive or promotional content");
                    break;
                case "MISINFORMATION":
                    recommendations.add("Verify information accuracy before sharing");
                    break;
            }
        }
        
        return recommendations;
    }

    /**
     * Calculate content score (0-1, higher is better)
     */
    private double calculateContentScore(String content, List<String> violations) {
        if (content == null || content.trim().isEmpty()) {
            return 0.0;
        }
        
        double score = 1.0;
        
        // Deduct points for violations
        score -= violations.size() * 0.2;
        
        // Deduct points for content length (too short or too long)
        if (content.length() < 10) {
            score -= 0.3;
        } else if (content.length() > 10000) {
            score -= 0.2;
        }
        
        // Deduct points for excessive punctuation
        long punctuationCount = content.chars()
            .filter(ch -> "!@#$%^&*()_+-=[]{}|;':\",./<>?".indexOf(ch) >= 0)
            .count();
        
        if (punctuationCount > content.length() * 0.1) {
            score -= 0.2;
        }
        
        return Math.max(0.0, Math.min(1.0, score));
    }

    /**
     * Add content to blocked list
     */
    public void addBlockedContent(String userId, String content) {
        blockedContent.computeIfAbsent(userId, k -> new ArrayList<>()).add(content);
    }

    /**
     * Add content to allowed list
     */
    public void addAllowedContent(String userId, String content) {
        allowedContent.computeIfAbsent(userId, k -> new ArrayList<>()).add(content);
    }

    /**
     * Remove content from blocked list
     */
    public void removeBlockedContent(String userId, String content) {
        List<String> userBlocked = blockedContent.get(userId);
        if (userBlocked != null) {
            userBlocked.remove(content);
        }
    }

    /**
     * Remove content from allowed list
     */
    public void removeAllowedContent(String userId, String content) {
        List<String> userAllowed = allowedContent.get(userId);
        if (userAllowed != null) {
            userAllowed.remove(content);
        }
    }

    /**
     * Get filter settings for a user
     */
    public Map<String, Object> getFilterSettings(String userId) {
        return filterSettings.getOrDefault(userId, new HashMap<>());
    }

    /**
     * Update filter settings for a user
     */
    public void updateFilterSettings(String userId, Map<String, Object> settings) {
        filterSettings.put(userId, settings);
    }

    /**
     * Get filter statistics
     */
    public Map<String, Object> getFilterStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalBlocked = blockedContent.values().stream()
            .mapToLong(List::size)
            .sum();
        
        long totalAllowed = allowedContent.values().stream()
            .mapToLong(List::size)
            .sum();
        
        stats.put("totalBlockedContent", totalBlocked);
        stats.put("totalAllowedContent", totalAllowed);
        stats.put("usersWithFilters", filterSettings.size());
        
        return stats;
    }

    /**
     * Clear filter data for a user
     */
    public void clearFilterData(String userId) {
        blockedContent.remove(userId);
        allowedContent.remove(userId);
        filterSettings.remove(userId);
    }
}