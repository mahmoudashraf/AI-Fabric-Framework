package com.ai.infrastructure.service;

import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.ai.infrastructure.entity.Behavior;
import com.ai.infrastructure.repository.BehaviorRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
    
    private final BehaviorRepository behaviorRepository;
    private final AICapabilityService aiCapabilityService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String FUNNEL_PATTERN_FLAG = "FUNNEL_COMPLETED";
    private static final String EVENING_PATTERN_FLAG = "EVENING_SHOPPER";
    private static final String WEEKEND_PATTERN_FLAG = "WEEKEND_PREFERENCE";
    
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
        aiCapabilityService.processEntityForAI(savedBehavior, "behavior");
        
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
        
        List<Behavior> behaviors = behaviorRepository.findByUserIdOrderByCreatedAtDesc(userId);
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
        
        Page<Behavior> behaviors = behaviorRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
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
    @Transactional
    public BehaviorAnalysisResult analyzeBehaviors(UUID userId) {
        log.info("Analyzing behaviors for user: {}", userId);

        List<Behavior> behaviors = behaviorRepository.findByUserIdOrderByCreatedAtDesc(userId);
        if (behaviors.isEmpty()) {
            return BehaviorAnalysisResult.builder()
                .analysisId(UUID.randomUUID().toString())
                .userId(userId.toString())
                .analysisType("behavior_pattern_detection")
                .summary("No behavioral data available for analysis")
                .insights(List.of("Collect more behavioral events to enable pattern detection"))
                .patterns(List.of())
                .recommendations(List.of("Encourage user engagement to capture behavioral signals"))
                .confidenceScore(0.3)
                .significanceScore(0.2)
                .analyzedAt(LocalDateTime.now())
                .build();
        }

        List<Behavior> chronological = new ArrayList<>(behaviors);
        chronological.sort(Comparator.comparing(Behavior::getCreatedAt));

        int totalEvents = chronological.size();
        long viewCount = chronological.stream()
            .filter(b -> b.getBehaviorType() == Behavior.BehaviorType.PRODUCT_VIEW
                || b.getBehaviorType() == Behavior.BehaviorType.VIEW
                || b.getBehaviorType() == Behavior.BehaviorType.PAGE_VIEW)
            .count();
        long addToCartCount = chronological.stream()
            .filter(b -> b.getBehaviorType() == Behavior.BehaviorType.ADD_TO_CART)
            .count();
        long purchaseCount = chronological.stream()
            .filter(b -> b.getBehaviorType() == Behavior.BehaviorType.PURCHASE)
            .count();

        boolean funnelDetected = false;
        boolean viewSeen = false;
        boolean cartSeen = false;
        for (Behavior behavior : chronological) {
            switch (behavior.getBehaviorType()) {
                case PRODUCT_VIEW, VIEW, PAGE_VIEW -> viewSeen = true;
                case ADD_TO_CART -> {
                    if (viewSeen) {
                        cartSeen = true;
                    }
                }
                case PURCHASE -> {
                    if (viewSeen && cartSeen) {
                        funnelDetected = true;
                    }
                }
                default -> {
                    // no-op
                }
            }
            if (funnelDetected) {
                break;
            }
        }

        double completionRate = viewCount == 0 ? 0.0 : (double) purchaseCount / viewCount;

        long eveningCount = chronological.stream()
            .filter(b -> Optional.ofNullable(b.getCreatedAt())
                .map(LocalDateTime::toLocalTime)
                .map(time -> !time.isBefore(LocalTime.of(18, 0)) && !time.isAfter(LocalTime.of(21, 59)))
                .orElse(false))
            .count();
        boolean eveningPattern = totalEvents > 0 && (double) eveningCount / totalEvents >= 0.6;

        long weekendCount = chronological.stream()
            .filter(b -> Optional.ofNullable(b.getCreatedAt())
                .map(LocalDateTime::getDayOfWeek)
                .map(day -> day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY)
                .orElse(false))
            .count();
        boolean weekendPreference = totalEvents > 0 && (double) weekendCount / totalEvents >= 0.5;

        Map<String, Long> categoryFrequency = new LinkedHashMap<>();
        chronological.forEach(behavior -> extractCategory(behavior)
            .ifPresent(category -> categoryFrequency.merge(category.toLowerCase(), 1L, Long::sum)));

        String topCategory = categoryFrequency.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
        boolean categoryAffinity = topCategory != null && categoryFrequency.get(topCategory) >= Math.max(3, totalEvents * 0.3);

        List<String> patterns = new ArrayList<>();
        List<String> insights = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        Set<String> patternFlags = new LinkedHashSet<>();

        if (funnelDetected) {
            patternFlags.add(FUNNEL_PATTERN_FLAG);
            patterns.add(String.format("Browse → Cart → Purchase funnel detected (completion rate %.0f%%)", completionRate * 100));
            insights.add("User completes the purchase funnel after engaging with products");
            recommendations.add("Reinforce post-cart nudges to maintain funnel completion strength");
        } else {
            recommendations.add("Introduce targeted incentives to improve cart-to-purchase conversion");
        }

        if (eveningPattern) {
            patternFlags.add(EVENING_PATTERN_FLAG);
            patterns.add(String.format("Evening shopping preference detected (%d of %d events after 6pm)", eveningCount, totalEvents));
            insights.add("Behavior peaks during evening hours, schedule campaigns accordingly");
            recommendations.add("Schedule personalized offers between 6pm and 10pm local time");
        }

        if (weekendPreference) {
            patternFlags.add(WEEKEND_PATTERN_FLAG);
            patterns.add(String.format("Weekend activity spike detected (%d weekend events)", weekendCount));
            insights.add("User displays stronger engagement on weekends");
            recommendations.add("Launch exclusive weekend bundles to capitalize on engagement spike");
        }

        if (categoryAffinity && topCategory != null) {
            String categoryFlag = "CATEGORY_" + topCategory.toUpperCase();
            patternFlags.add(categoryFlag);
            patterns.add(String.format("Category affinity detected: %s (%,d interactions)", topCategory, categoryFrequency.get(topCategory)));
            insights.add(String.format("User shows sustained interest in %s products", topCategory));
            recommendations.add(String.format("Curate personalized recommendations for %s items", topCategory));
        }

        if (patterns.isEmpty()) {
            patterns.add("No strong behavioral patterns identified");
            insights.add("Current activity volume is insufficient for reliable pattern detection");
            recommendations.add("Capture additional behavioral signals to surface patterns");
        }

        double confidence = 0.5 + patternFlags.size() * 0.12;
        if (funnelDetected) {
            confidence += 0.1;
        }
        confidence = Math.min(0.95, confidence);
        double significance = Math.min(0.9, completionRate * 0.5 + (patternFlags.size() * 0.1) + 0.3);

        persistPatternFlags(behaviors, patternFlags);

        return BehaviorAnalysisResult.builder()
            .analysisId(UUID.randomUUID().toString())
            .userId(userId.toString())
            .analysisType("behavior_pattern_detection")
            .summary(String.format("Analyzed %,d behavioral events", totalEvents))
            .insights(insights)
            .patterns(patterns)
            .recommendations(recommendations)
            .confidenceScore(confidence)
            .significanceScore(significance)
            .analyzedAt(LocalDateTime.now())
            .build();
    }

    private void persistPatternFlags(List<Behavior> behaviors, Set<String> patternFlags) {
        String serializedFlags = serializePatternFlags(patternFlags);
        behaviors.forEach(behavior -> behavior.setPatternFlags(serializedFlags));
        behaviorRepository.saveAll(behaviors);
    }

    private String serializePatternFlags(Set<String> patternFlags) {
        if (patternFlags.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(patternFlags);
        } catch (JsonProcessingException exception) {
            log.warn("Unable to serialize pattern flags, falling back to comma-separated string", exception);
            return String.join(",", patternFlags);
        }
    }

    private Optional<String> extractCategory(Behavior behavior) {
        List<String> candidates = new ArrayList<>();
        if (behavior.getMetadata() != null) {
            parseMetadataValue(behavior.getMetadata(), "category").ifPresent(candidates::add);
            parseMetadataValue(behavior.getMetadata(), "categories")
                .ifPresent(value -> candidates.add(value.split(",")[0]));
        }
        if (behavior.getEntityType() != null && behavior.getEntityType().toLowerCase().contains("watch")) {
            candidates.add("watches");
        }
        return candidates.stream().filter(candidate -> !candidate.isBlank()).findFirst();
    }

    private Optional<String> parseMetadataValue(String metadataJson, String key) {
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
            Object value = parsed.get(key);
            if (value instanceof String stringValue && !stringValue.isBlank()) {
                return Optional.of(stringValue);
            }
            if (value != null) {
                return Optional.of(value.toString());
            }
        } catch (Exception ignored) {
            // ignore parsing issues
        }
        return Optional.empty();
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
        aiCapabilityService.processEntityForAI(savedBehavior, "behavior");
        
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
}