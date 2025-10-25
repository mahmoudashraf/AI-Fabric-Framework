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

import java.time.LocalDateTime;
import java.util.List;
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
        aiCapabilityService.processEntity(savedBehavior);
        
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
    @Transactional(readOnly = true)
    public BehaviorAnalysisResult analyzeBehaviors(UUID userId) {
        log.info("Analyzing behaviors for user: {}", userId);
        
        List<Behavior> behaviors = behaviorRepository.findByUserId(userId);
        
        // Use AI capability service for analysis
        return aiCapabilityService.analyzeBehaviors(behaviors);
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
        aiCapabilityService.processEntity(savedBehavior);
        
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