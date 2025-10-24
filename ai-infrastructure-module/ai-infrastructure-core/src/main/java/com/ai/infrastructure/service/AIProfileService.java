package com.ai.infrastructure.service;

import com.ai.infrastructure.dto.AIProfileRequest;
import com.ai.infrastructure.dto.AIProfileResponse;
import com.ai.infrastructure.entity.AIProfile;
import com.ai.infrastructure.repository.AIProfileRepository;
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
 * Generic AI Profile Service
 * 
 * Service for AI profile operations.
 * This service is domain-agnostic and can be used across different applications.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AIProfileService {
    
    private final AIProfileRepository aiProfileRepository;
    private final AICapabilityService aiCapabilityService;
    
    /**
     * Create a new AI profile
     */
    public AIProfileResponse createAIProfile(AIProfileRequest request) {
        log.info("Creating AI profile for user: {}", request.getUserId());
        
        AIProfile aiProfile = AIProfile.builder()
                .userId(UUID.fromString(request.getUserId()))
                .preferences(request.getPreferences())
                .interests(request.getInterests())
                .behaviorPatterns(request.getBehaviorPatterns())
                .cvFileUrl(request.getCvFileUrl())
                .status(AIProfile.AIProfileStatus.valueOf(request.getStatus() != null ? request.getStatus() : "ACTIVE"))
                .confidenceScore(request.getConfidenceScore())
                .version(request.getVersion() != null ? request.getVersion() : 1)
                .build();
        
        AIProfile savedProfile = aiProfileRepository.save(aiProfile);
        
        // Process with AI capabilities
        aiCapabilityService.processEntityForAI(savedProfile, "ai_profile");
        
        return mapToResponse(savedProfile);
    }
    
    /**
     * Get AI profile by ID
     */
    @Transactional(readOnly = true)
    public AIProfileResponse getAIProfileById(UUID id) {
        log.info("Getting AI profile by ID: {}", id);
        
        AIProfile aiProfile = aiProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AI Profile not found with ID: " + id));
        
        return mapToResponse(aiProfile);
    }
    
    /**
     * Get AI profile by user ID
     */
    @Transactional(readOnly = true)
    public AIProfileResponse getAIProfileByUserId(UUID userId) {
        log.info("Getting AI profile for user: {}", userId);
        
        AIProfile aiProfile = aiProfileRepository.findByUserId(userId);
        if (aiProfile == null) {
            throw new RuntimeException("AI Profile not found for user: " + userId);
        }
        
        return mapToResponse(aiProfile);
    }
    
    /**
     * Get AI profiles by status
     */
    @Transactional(readOnly = true)
    public List<AIProfileResponse> getAIProfilesByStatus(AIProfile.AIProfileStatus status) {
        log.info("Getting AI profiles by status: {}", status);
        
        List<AIProfile> profiles = aiProfileRepository.findByStatus(status);
        return profiles.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get AI profiles by status with pagination
     */
    @Transactional(readOnly = true)
    public Page<AIProfileResponse> getAIProfilesByStatus(AIProfile.AIProfileStatus status, Pageable pageable) {
        log.info("Getting AI profiles by status: {} with pagination", status);
        
        Page<AIProfile> profiles = aiProfileRepository.findByStatus(status, pageable);
        return profiles.map(this::mapToResponse);
    }
    
    /**
     * Get AI profiles by user ID and status
     */
    @Transactional(readOnly = true)
    public List<AIProfileResponse> getAIProfilesByUserIdAndStatus(UUID userId, AIProfile.AIProfileStatus status) {
        log.info("Getting AI profiles for user: {} and status: {}", userId, status);
        
        List<AIProfile> profiles = aiProfileRepository.findByUserIdAndStatus(userId, status);
        return profiles.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get AI profiles by confidence score range
     */
    @Transactional(readOnly = true)
    public List<AIProfileResponse> getAIProfilesByConfidenceScoreRange(Double minScore, Double maxScore) {
        log.info("Getting AI profiles by confidence score range: {} - {}", minScore, maxScore);
        
        List<AIProfile> profiles = aiProfileRepository.findByConfidenceScoreBetween(minScore, maxScore);
        return profiles.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get AI profiles by version
     */
    @Transactional(readOnly = true)
    public List<AIProfileResponse> getAIProfilesByVersion(Integer version) {
        log.info("Getting AI profiles by version: {}", version);
        
        List<AIProfile> profiles = aiProfileRepository.findByVersion(version);
        return profiles.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get AI profiles by user ID and version
     */
    @Transactional(readOnly = true)
    public List<AIProfileResponse> getAIProfilesByUserIdAndVersion(UUID userId, Integer version) {
        log.info("Getting AI profiles for user: {} and version: {}", userId, version);
        
        List<AIProfile> profiles = aiProfileRepository.findByUserIdAndVersion(userId, version);
        return profiles.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get AI profiles by date range
     */
    @Transactional(readOnly = true)
    public List<AIProfileResponse> getAIProfilesByDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Getting AI profiles from {} to {}", startDate, endDate);
        
        List<AIProfile> profiles = aiProfileRepository.findByCreatedAtBetween(startDate, endDate);
        return profiles.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get AI profiles by user ID and date range
     */
    @Transactional(readOnly = true)
    public List<AIProfileResponse> getAIProfilesByUserIdAndDateRange(UUID userId, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Getting AI profiles for user: {} from {} to {}", userId, startDate, endDate);
        
        List<AIProfile> profiles = aiProfileRepository.findByUserIdAndCreatedAtBetween(userId, startDate, endDate);
        return profiles.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get AI profiles by user ID and date range with pagination
     */
    @Transactional(readOnly = true)
    public Page<AIProfileResponse> getAIProfilesByUserIdAndDateRange(UUID userId, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        log.info("Getting AI profiles for user: {} from {} to {} with pagination", userId, startDate, endDate);
        
        Page<AIProfile> profiles = aiProfileRepository.findByUserIdAndCreatedAtBetween(userId, startDate, endDate, pageable);
        return profiles.map(this::mapToResponse);
    }
    
    /**
     * Get latest AI profile by user ID
     */
    @Transactional(readOnly = true)
    public AIProfileResponse getLatestAIProfileByUserId(UUID userId) {
        log.info("Getting latest AI profile for user: {}", userId);
        
        AIProfile aiProfile = aiProfileRepository.findLatestProfileByUserId(userId);
        if (aiProfile == null) {
            throw new RuntimeException("No AI Profile found for user: " + userId);
        }
        
        return mapToResponse(aiProfile);
    }
    
    /**
     * Update AI profile
     */
    public AIProfileResponse updateAIProfile(UUID id, AIProfileRequest request) {
        log.info("Updating AI profile: {}", id);
        
        AIProfile aiProfile = aiProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AI Profile not found with ID: " + id));
        
        aiProfile.setPreferences(request.getPreferences());
        aiProfile.setInterests(request.getInterests());
        aiProfile.setBehaviorPatterns(request.getBehaviorPatterns());
        aiProfile.setCvFileUrl(request.getCvFileUrl());
        if (request.getStatus() != null) {
            aiProfile.setStatus(AIProfile.AIProfileStatus.valueOf(request.getStatus()));
        }
        aiProfile.setConfidenceScore(request.getConfidenceScore());
        if (request.getVersion() != null) {
            aiProfile.setVersion(request.getVersion());
        }
        
        AIProfile savedProfile = aiProfileRepository.save(aiProfile);
        
        // Process with AI capabilities
        aiCapabilityService.processEntityForAI(savedProfile, "ai_profile");
        
        return mapToResponse(savedProfile);
    }
    
    /**
     * Update AI profile by user ID
     */
    public AIProfileResponse updateAIProfileByUserId(UUID userId, AIProfileRequest request) {
        log.info("Updating AI profile for user: {}", userId);
        
        AIProfile aiProfile = aiProfileRepository.findByUserId(userId);
        if (aiProfile == null) {
            throw new RuntimeException("AI Profile not found for user: " + userId);
        }
        
        aiProfile.setPreferences(request.getPreferences());
        aiProfile.setInterests(request.getInterests());
        aiProfile.setBehaviorPatterns(request.getBehaviorPatterns());
        aiProfile.setCvFileUrl(request.getCvFileUrl());
        if (request.getStatus() != null) {
            aiProfile.setStatus(AIProfile.AIProfileStatus.valueOf(request.getStatus()));
        }
        aiProfile.setConfidenceScore(request.getConfidenceScore());
        if (request.getVersion() != null) {
            aiProfile.setVersion(request.getVersion());
        }
        
        AIProfile savedProfile = aiProfileRepository.save(aiProfile);
        
        // Process with AI capabilities
        aiCapabilityService.processEntityForAI(savedProfile, "ai_profile");
        
        return mapToResponse(savedProfile);
    }
    
    /**
     * Delete AI profile
     */
    public void deleteAIProfile(UUID id) {
        log.info("Deleting AI profile: {}", id);
        
        AIProfile aiProfile = aiProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("AI Profile not found with ID: " + id));
        
        aiProfileRepository.delete(aiProfile);
    }
    
    /**
     * Delete AI profile by user ID
     */
    public void deleteAIProfileByUserId(UUID userId) {
        log.info("Deleting AI profile for user: {}", userId);
        
        AIProfile aiProfile = aiProfileRepository.findByUserId(userId);
        if (aiProfile == null) {
            throw new RuntimeException("AI Profile not found for user: " + userId);
        }
        
        aiProfileRepository.delete(aiProfile);
    }
    
    /**
     * Map entity to response DTO
     */
    private AIProfileResponse mapToResponse(AIProfile aiProfile) {
        return AIProfileResponse.builder()
                .id(aiProfile.getId().toString())
                .userId(aiProfile.getUserId().toString())
                .preferences(aiProfile.getPreferences())
                .interests(aiProfile.getInterests())
                .behaviorPatterns(aiProfile.getBehaviorPatterns())
                .cvFileUrl(aiProfile.getCvFileUrl())
                .status(aiProfile.getStatus().toString())
                .confidenceScore(aiProfile.getConfidenceScore())
                .version(aiProfile.getVersion())
                .createdAt(aiProfile.getCreatedAt())
                .updatedAt(aiProfile.getUpdatedAt())
                .build();
    }
}