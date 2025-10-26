package com.easyluxury.ai.adapter;

import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.ai.infrastructure.dto.AIProfileRequest;
import com.ai.infrastructure.dto.AIProfileResponse;
import com.ai.infrastructure.service.BehaviorService;
import com.ai.infrastructure.service.AIInfrastructureProfileService;
import com.easyluxury.entity.User;
import com.easyluxury.entity.UserBehavior;
import com.easyluxury.entity.AIProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * User AI Adapter
 * 
 * Adapter to bridge between User domain services and generic AI infrastructure services.
 * This adapter handles the conversion between domain-specific entities and generic AI DTOs.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserAIAdapter {
    
    private final BehaviorService behaviorService;
    private final AIInfrastructureProfileService aiProfileService;
    
    /**
     * Track user behavior using AI infrastructure
     */
    public BehaviorResponse trackUserBehavior(User user, UserBehavior userBehavior) {
        log.info("Tracking behavior for user: {}", user.getId());
        
        BehaviorRequest request = BehaviorRequest.builder()
                .userId(user.getId().toString())
                .behaviorType(userBehavior.getBehaviorType().toString())
                .entityType(userBehavior.getEntityType())
                .entityId(userBehavior.getEntityId())
                .action(userBehavior.getAction())
                .context(userBehavior.getContext())
                .metadata(userBehavior.getMetadata())
                .sessionId(userBehavior.getSessionId())
                .deviceInfo(userBehavior.getDeviceInfo())
                .locationInfo(userBehavior.getLocationInfo())
                .durationSeconds(userBehavior.getDurationSeconds())
                .value(userBehavior.getValue())
                .build();
        
        return behaviorService.createBehavior(request);
    }
    
    /**
     * Get user behaviors using AI infrastructure
     */
    public List<BehaviorResponse> getUserBehaviors(User user) {
        log.info("Getting behaviors for user: {}", user.getId());
        
        return behaviorService.getBehaviorsByUserId(user.getId());
    }
    
    /**
     * Get user behaviors by type using AI infrastructure
     */
    public List<BehaviorResponse> getUserBehaviorsByType(User user, UserBehavior.BehaviorType behaviorType) {
        log.info("Getting behaviors for user: {} and type: {}", user.getId(), behaviorType);
        
        return behaviorService.getBehaviorsByUserIdAndType(user.getId(), 
                com.ai.infrastructure.entity.Behavior.BehaviorType.valueOf(behaviorType.toString()));
    }
    
    /**
     * Analyze user behaviors using AI infrastructure
     */
    public BehaviorAnalysisResult analyzeUserBehaviors(User user) {
        log.info("Analyzing behaviors for user: {}", user.getId());
        
        return behaviorService.analyzeBehaviors(user.getId());
    }
    
    /**
     * Create AI profile for user using AI infrastructure
     */
    public AIProfileResponse createUserAIProfile(User user, AIProfile aiProfile) {
        log.info("Creating AI profile for user: {}", user.getId());
        
        AIProfileRequest request = AIProfileRequest.builder()
                .userId(user.getId().toString())
                .preferences(aiProfile.getAiAttributes())
                .interests(null) // Map from aiProfile if needed
                .behaviorPatterns(null) // Map from aiProfile if needed
                .cvFileUrl(aiProfile.getCvFileUrl())
                .status(aiProfile.getStatus().toString())
                .confidenceScore(null) // Set default or calculate
                .version(1)
                .build();
        
        return aiProfileService.createAIInfrastructureProfile(request);
    }
    
    /**
     * Get AI profile for user using AI infrastructure
     */
    public AIProfileResponse getUserAIProfile(User user) {
        log.info("Getting AI profile for user: {}", user.getId());
        
        return aiProfileService.getAIInfrastructureProfileByUserId(user.getId());
    }
    
    /**
     * Update AI profile for user using AI infrastructure
     */
    public AIProfileResponse updateUserAIProfile(User user, AIProfile aiProfile) {
        log.info("Updating AI profile for user: {}", user.getId());
        
        AIProfileRequest request = AIProfileRequest.builder()
                .userId(user.getId().toString())
                .preferences(aiProfile.getAiAttributes())
                .interests(null) // Map from aiProfile if needed
                .behaviorPatterns(null) // Map from aiProfile if needed
                .cvFileUrl(aiProfile.getCvFileUrl())
                .status(aiProfile.getStatus().toString())
                .confidenceScore(null) // Set default or calculate
                .version(1)
                .build();
        
        return aiProfileService.updateAIInfrastructureProfileByUserId(user.getId(), request);
    }
    
    /**
     * Delete AI profile for user using AI infrastructure
     */
    public void deleteUserAIProfile(User user) {
        log.info("Deleting AI profile for user: {}", user.getId());
        
        aiProfileService.deleteAIInfrastructureProfileByUserId(user.getId());
    }
    
    /**
     * Get user behaviors by date range using AI infrastructure
     */
    public List<BehaviorResponse> getUserBehaviorsByDateRange(User user, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Getting behaviors for user: {} from {} to {}", user.getId(), startDate, endDate);
        
        return behaviorService.getBehaviorsByUserIdAndDateRange(user.getId(), startDate, endDate);
    }
    
    /**
     * Get user behaviors by session using AI infrastructure
     */
    public List<BehaviorResponse> getUserBehaviorsBySession(User user, String sessionId) {
        log.info("Getting behaviors for user: {} and session: {}", user.getId(), sessionId);
        
        return behaviorService.getBehaviorsBySession(sessionId).stream()
                .filter(behavior -> behavior.getUserId().equals(user.getId().toString()))
                .collect(Collectors.toList());
    }
    
    /**
     * Get user behaviors by entity using AI infrastructure
     */
    public List<BehaviorResponse> getUserBehaviorsByEntity(User user, String entityType, String entityId) {
        log.info("Getting behaviors for user: {} and entity: {} - {}", user.getId(), entityType, entityId);
        
        return behaviorService.getBehaviorsByEntity(entityType, entityId).stream()
                .filter(behavior -> behavior.getUserId().equals(user.getId().toString()))
                .collect(Collectors.toList());
    }
}