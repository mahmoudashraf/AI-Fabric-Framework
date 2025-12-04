package com.ai.infrastructure.service;

import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.ai.infrastructure.entity.Behavior;
import com.ai.infrastructure.repository.BehaviorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generic Behavior Service
 * 
 * Service for behavioral data operations.
 * This service is domain-agnostic and can be used across different applications.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class BehaviorService {
    
    private static final String BEHAVIOR_ENTITY_TYPE = "behavior";
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("\"category\"\\s*:\\s*\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);

    private final BehaviorRepository behaviorRepository;
    private final AICapabilityService aiCapabilityService;
    
    /**
     * Create a new behavior record
     */
    public BehaviorResponse createBehavior(BehaviorRequest request) {
        log.info("Creating behavior for user: {}", request.getUserId());
        
        Behavior behavior = Behavior.builder()
                .userId(UUID.fromString(request.getUserId()))
                .behaviorType(Behavior.BehaviorType.valueOf(request.getBehaviorType()))
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .action(request.getAction())
                .context(request.getContext())
                .metadata(request.getMetadata())
                .sessionId(request.getSessionId())
                .deviceInfo(request.getDeviceInfo())
                .locationInfo(request.getLocationInfo())
                .durationSeconds(request.getDurationSeconds())
                .value(request.getValue())
                .build();
        
        Behavior savedBehavior = behaviorRepository.save(behavior);

        // Process with AI capabilities
        aiCapabilityService.processEntityForAI(savedBehavior, BEHAVIOR_ENTITY_TYPE);

        return mapToResponse(savedBehavior);
    }
    
    /**
     * Get behavior by ID
     */
    @Transactional(readOnly = true)
    public BehaviorResponse getBehaviorById(UUID id) {
        log.info("Getting behavior by ID: {}", id);
        
        Behavior behavior = behaviorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Behavior not found with ID: " + id));
        
        return mapToResponse(behavior);
    }
    
    /**
     * Get behaviors by user ID
     */
    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsByUserId(UUID userId) {
        log.info("Getting behaviors for user: {}", userId);
        
        List<Behavior> behaviors = behaviorRepository.findByUserId(userId);
        return behaviors.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get behaviors by user ID with pagination
     */
    @Transactional(readOnly = true)
    public Page<BehaviorResponse> getBehaviorsByUserId(UUID userId, Pageable pageable) {
        log.info("Getting behaviors for user: {} with pagination", userId);
        
        Page<Behavior> behaviors = behaviorRepository.findByUserId(userId, pageable);
        return behaviors.map(this::mapToResponse);
    }
    
    /**
     * Get behaviors by behavior type
     */
    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsByType(Behavior.BehaviorType behaviorType) {
        log.info("Getting behaviors by type: {}", behaviorType);
        
        List<Behavior> behaviors = behaviorRepository.findByBehaviorType(behaviorType);
        return behaviors.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get behaviors by behavior type with pagination
     */
    @Transactional(readOnly = true)
    public Page<BehaviorResponse> getBehaviorsByType(Behavior.BehaviorType behaviorType, Pageable pageable) {
        log.info("Getting behaviors by type: {} with pagination", behaviorType);
        
        Page<Behavior> behaviors = behaviorRepository.findByBehaviorType(behaviorType, pageable);
        return behaviors.map(this::mapToResponse);
    }
    
    /**
     * Get behaviors by user ID and behavior type
     */
    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsByUserIdAndType(UUID userId, Behavior.BehaviorType behaviorType) {
        log.info("Getting behaviors for user: {} and type: {}", userId, behaviorType);
        
        List<Behavior> behaviors = behaviorRepository.findByUserIdAndBehaviorType(userId, behaviorType);
        return behaviors.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get behaviors by entity type and entity ID
     */
    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsByEntity(String entityType, String entityId) {
        log.info("Getting behaviors for entity: {} - {}", entityType, entityId);
        
        List<Behavior> behaviors = behaviorRepository.findByEntityTypeAndEntityId(entityType, entityId);
        return behaviors.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get behaviors by session ID
     */
    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsBySession(String sessionId) {
        log.info("Getting behaviors for session: {}", sessionId);
        
        List<Behavior> behaviors = behaviorRepository.findBySessionId(sessionId);
        return behaviors.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get behaviors by date range
     */
    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Getting behaviors from {} to {}", startDate, endDate);
        
        List<Behavior> behaviors = behaviorRepository.findByCreatedAtBetween(startDate, endDate);
        return behaviors.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get behaviors by user ID and date range
     */
    @Transactional(readOnly = true)
    public List<BehaviorResponse> getBehaviorsByUserIdAndDateRange(UUID userId, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Getting behaviors for user: {} from {} to {}", userId, startDate, endDate);
        
        List<Behavior> behaviors = behaviorRepository.findByUserIdAndCreatedAtBetween(userId, startDate, endDate);
        return behaviors.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get behaviors by user ID and date range with pagination
     */
    @Transactional(readOnly = true)
    public Page<BehaviorResponse> getBehaviorsByUserIdAndDateRange(UUID userId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        log.info("Getting behaviors for user: {} from {} to {} with pagination", userId, startDate, endDate);
        
        Page<Behavior> behaviors = behaviorRepository.findByUserIdAndCreatedAtBetween(userId, startDate, endDate, pageable);
        return behaviors.map(this::mapToResponse);
    }
    
    /**
     * Analyze behaviors for a user
     */
    public BehaviorAnalysisResult analyzeBehaviors(UUID userId) {
        log.info("Analyzing behaviors for user: {}", userId);

        List<Behavior> behaviors = behaviorRepository.findByUserId(userId);
        if (behaviors.isEmpty()) {
            return BehaviorAnalysisResult.builder()
                .analysisId(UUID.randomUUID().toString())
                .userId(userId.toString())
                .analysisType("behavioral-baseline")
                .summary("No behavioral data available for analysis.")
                .insights(List.of("Collect additional behavioral events to bootstrap AI insights."))
                .patterns(List.of())
                .recommendations(List.of("Verify ingestion jobs for behavior signals."))
                .confidenceScore(0.2)
                .significanceScore(0.15)
                .metrics(Map.of("totalBehaviors", 0))
                .metadata(Map.of())
                .analyzedAt(LocalDateTime.now())
                .validUntil(LocalDateTime.now().plusDays(7))
                .build();
        }

        List<Behavior> sortedBehaviors = behaviors.stream()
            .sorted(Comparator.comparing(
                Behavior::getCreatedAt,
                Comparator.nullsLast(Comparator.naturalOrder())
            ))
            .collect(Collectors.toList());

        Map<Behavior.BehaviorType, Long> typeCounts = behaviors.stream()
            .filter(b -> b.getBehaviorType() != null)
            .collect(Collectors.groupingBy(Behavior::getBehaviorType, Collectors.counting()));

        long viewCount = typeCounts.getOrDefault(Behavior.BehaviorType.PRODUCT_VIEW, 0L);
        long addToCartCount = typeCounts.getOrDefault(Behavior.BehaviorType.ADD_TO_CART, 0L);
        long purchaseCount = typeCounts.getOrDefault(Behavior.BehaviorType.PURCHASE, 0L);
        boolean funnelCompleted = viewCount > 0 && addToCartCount > 0 && purchaseCount > 0;

        long eveningEvents = behaviors.stream()
            .map(Behavior::getCreatedAt)
            .filter(Objects::nonNull)
            .filter(ts -> ts.getHour() >= 18 || ts.getHour() < 6)
            .count();
        boolean eveningPreference = eveningEvents >= Math.max(3, Math.round(behaviors.size() * 0.4));

        long weekendEvents = behaviors.stream()
            .map(Behavior::getCreatedAt)
            .filter(Objects::nonNull)
            .filter(ts -> {
                DayOfWeek dayOfWeek = ts.getDayOfWeek();
                return dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY;
            })
            .count();
        boolean weekendPreference = weekendEvents >= Math.max(3, Math.round(behaviors.size() * 0.35));

        Map<String, Long> categoryCounts = behaviors.stream()
            .map(Behavior::getMetadata)
            .filter(Objects::nonNull)
            .map(this::extractCategory)
            .filter(Objects::nonNull)
            .collect(Collectors.groupingBy(String::toLowerCase, Collectors.counting()));

        Optional<Map.Entry<String, Long>> dominantCategory = categoryCounts.entrySet().stream()
            .max(Map.Entry.comparingByValue());

        List<String> patterns = new ArrayList<>();
        List<String> insights = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        List<String> patternFlags = new ArrayList<>();

        if (funnelCompleted) {
            patterns.add("Browse → cart → purchase funnel detected.");
            insights.add(String.format(Locale.ENGLISH,
                "Identified %d product views, %d cart interactions, and %d purchases.",
                viewCount, addToCartCount, purchaseCount));
            recommendations.add("Upsell high-intent users with exclusive bundles.");
            patternFlags.add("FUNNEL_COMPLETED");
        }

        if (eveningPreference) {
            patterns.add("Evening shopping preference detected (peak activity after 6PM).");
            insights.add(String.format(Locale.ENGLISH,
                "%d evening/night events captured in the current analysis window.", eveningEvents));
            recommendations.add("Schedule outbound campaigns for evening hours.");
            patternFlags.add("EVENING_SHOPPER");
        }

        if (weekendPreference) {
            patterns.add("Weekend engagement spike observed.");
            insights.add(String.format(Locale.ENGLISH,
                "%d weekend interactions indicate leisure-time browsing.", weekendEvents));
            recommendations.add("Launch limited-time weekend incentives.");
            patternFlags.add("WEEKEND_PREFERENCE");
        }

        dominantCategory.ifPresent(entry -> {
            patterns.add("Category affinity detected for " + entry.getKey() + ".");
            insights.add(String.format(Locale.ENGLISH,
                "Category \"%s\" accounts for %d interactions.", entry.getKey(), entry.getValue()));
            recommendations.add("Personalize feeds with more " + entry.getKey() + " inventory.");
            patternFlags.add("CATEGORY_AFFINITY");
        });

        if (patterns.isEmpty()) {
            patterns.add("Behavioral activity recorded but no dominant AI patterns detected.");
            recommendations.add("Capture richer metadata (category, price, channel) to unlock insights.");
        }

        double confidenceScore = Math.min(0.5 + patterns.size() * 0.15, 0.95);
        double significanceScore = Math.min(0.4 + patterns.size() * 0.12, 0.9);

        Map<String, Object> metrics = new HashMap<>();
        metrics.put("totalBehaviors", behaviors.size());
        metrics.put("uniqueSessions", behaviors.stream()
            .map(Behavior::getSessionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet())
            .size());
        metrics.put("funnelsDetected", funnelCompleted ? 1 : 0);
        metrics.put("eveningEvents", eveningEvents);
        metrics.put("weekendEvents", weekendEvents);
        dominantCategory.ifPresent(entry -> metrics.put("topCategory", entry.getKey()));

        String summary = String.format(Locale.ENGLISH,
            "Detected %d behavioral pattern(s) for user %s.", patterns.size(), userId);

        BehaviorAnalysisResult result = BehaviorAnalysisResult.builder()
            .analysisId(UUID.randomUUID().toString())
            .userId(userId.toString())
            .analysisType("behavioral-patterns")
            .summary(summary)
            .insights(insights)
            .patterns(patterns)
            .recommendations(recommendations)
            .confidenceScore(confidenceScore)
            .significanceScore(significanceScore)
            .metrics(metrics)
            .metadata(Map.of(
                "hasWeekendPreference", weekendPreference,
                "hasEveningPreference", eveningPreference,
                "hasFunnel", funnelCompleted
            ))
            .analyzedAt(LocalDateTime.now())
            .validUntil(LocalDateTime.now().plusDays(14))
            .build();

        sortedBehaviors.stream()
            .reduce((first, second) -> second)
            .ifPresent(latest -> {
                latest.setPatternFlags(String.join(",", patternFlags));
                latest.setAiInsights(String.join(" | ", patterns));
                latest.setAiAnalysis(summary);
                latest.setBehaviorScore(confidenceScore);
                latest.setSignificanceScore(significanceScore);
                behaviorRepository.save(latest);
            });

        return result;
    }
    
    /**
     * Update behavior
     */
    public BehaviorResponse updateBehavior(UUID id, BehaviorRequest request) {
        log.info("Updating behavior: {}", id);
        
        Behavior behavior = behaviorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Behavior not found with ID: " + id));
        
        behavior.setBehaviorType(Behavior.BehaviorType.valueOf(request.getBehaviorType()));
        behavior.setEntityType(request.getEntityType());
        behavior.setEntityId(request.getEntityId());
        behavior.setAction(request.getAction());
        behavior.setContext(request.getContext());
        behavior.setMetadata(request.getMetadata());
        behavior.setSessionId(request.getSessionId());
        behavior.setDeviceInfo(request.getDeviceInfo());
        behavior.setLocationInfo(request.getLocationInfo());
        behavior.setDurationSeconds(request.getDurationSeconds());
        behavior.setValue(request.getValue());
        
        Behavior savedBehavior = behaviorRepository.save(behavior);

        // Process with AI capabilities
        aiCapabilityService.processEntityForAI(savedBehavior, BEHAVIOR_ENTITY_TYPE);

        return mapToResponse(savedBehavior);
    }
    
    /**
     * Delete behavior
     */
    public void deleteBehavior(UUID id) {
        log.info("Deleting behavior: {}", id);
        
        Behavior behavior = behaviorRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Behavior not found with ID: " + id));
        
        behaviorRepository.delete(behavior);
    }
    
    /**
     * Map entity to response DTO
     */
    private BehaviorResponse mapToResponse(Behavior behavior) {
        return BehaviorResponse.builder()
                .id(behavior.getId().toString())
                .userId(behavior.getUserId().toString())
                .behaviorType(behavior.getBehaviorType().toString())
                .entityType(behavior.getEntityType())
                .entityId(behavior.getEntityId())
                .action(behavior.getAction())
                .context(behavior.getContext())
                .metadata(behavior.getMetadata())
                .sessionId(behavior.getSessionId())
                .deviceInfo(behavior.getDeviceInfo())
                .locationInfo(behavior.getLocationInfo())
                .durationSeconds(behavior.getDurationSeconds())
                .value(behavior.getValue())
                .aiAnalysis(behavior.getAiAnalysis())
                .aiInsights(behavior.getAiInsights())
                .behaviorScore(behavior.getBehaviorScore())
                .significanceScore(behavior.getSignificanceScore())
                .patternFlags(behavior.getPatternFlags())
                .createdAt(behavior.getCreatedAt())
                .build();
    }

    private String extractCategory(String metadata) {
        Matcher matcher = CATEGORY_PATTERN.matcher(metadata);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}