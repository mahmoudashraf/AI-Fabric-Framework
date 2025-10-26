package com.easyluxury.service;

import com.easyluxury.dto.AIProfileDto;
import com.easyluxury.entity.AIProfile;
import com.easyluxury.entity.AIProfileStatus;
import com.easyluxury.entity.User;
import com.easyluxury.exception.ResourceNotFoundException;
import com.easyluxury.mapper.AIProfileMapper;
import com.easyluxury.repository.AIProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service("easyLuxuryAIProfileService")
@RequiredArgsConstructor
public class AIProfileService {
    
    private final AIProfileRepository aiProfileRepository;
    private final AIProfileMapper aiProfileMapper;
    
    /**
     * Create a new AI profile
     */
    @Transactional
    public AIProfileDto createProfile(User user, String aiAttributes, String cvFileUrl) {
        log.debug("Creating AI profile for user: {}", user.getId());
        
        AIProfile aiProfile = AIProfile.builder()
            .user(user)
            .aiAttributes(aiAttributes)
            .cvFileUrl(cvFileUrl)
            .status(AIProfileStatus.DRAFT)
            .build();
        
        aiProfile = aiProfileRepository.save(aiProfile);
        
        log.info("Created AI profile {} for user {}", aiProfile.getId(), user.getId());
        
        return aiProfileMapper.toDto(aiProfile);
    }
    
    /**
     * Get AI profile by ID
     */
    @Transactional(readOnly = true)
    public AIProfileDto getProfileById(UUID profileId) {
        log.debug("Getting AI profile by ID: {}", profileId);
        
        AIProfile aiProfile = aiProfileRepository.findById(profileId)
            .orElseThrow(() -> new ResourceNotFoundException("AI Profile not found with id: " + profileId));
        
        return aiProfileMapper.toDto(aiProfile);
    }
    
    /**
     * Get latest AI profile for user
     */
    @Transactional(readOnly = true)
    public AIProfileDto getLatestProfileForUser(User user) {
        log.debug("Getting latest AI profile for user: {}", user.getId());
        
        AIProfile aiProfile = aiProfileRepository.findFirstByUserOrderByCreatedAtDesc(user)
            .orElseThrow(() -> new ResourceNotFoundException("No AI Profile found for user: " + user.getId()));
        
        return aiProfileMapper.toDto(aiProfile);
    }
    
    /**
     * Get all profiles for user
     */
    @Transactional(readOnly = true)
    public List<AIProfileDto> getAllProfilesForUser(User user) {
        log.debug("Getting all AI profiles for user: {}", user.getId());
        
        return aiProfileRepository.findByUserOrderByCreatedAtDesc(user)
            .stream()
            .map(aiProfileMapper::toDto)
            .collect(Collectors.toList());
    }
    
    /**
     * Update AI profile status
     */
    @Transactional
    public AIProfileDto updateProfileStatus(UUID profileId, AIProfileStatus status) {
        log.debug("Updating AI profile {} status to: {}", profileId, status);
        
        AIProfile aiProfile = aiProfileRepository.findById(profileId)
            .orElseThrow(() -> new ResourceNotFoundException("AI Profile not found with id: " + profileId));
        
        aiProfile.setStatus(status);
        aiProfile = aiProfileRepository.save(aiProfile);
        
        log.info("Updated AI profile {} status to {}", profileId, status);
        
        return aiProfileMapper.toDto(aiProfile);
    }
    
    /**
     * Update AI attributes
     */
    @Transactional
    public AIProfileDto updateAiAttributes(UUID profileId, String aiAttributes) {
        log.debug("Updating AI profile {} attributes", profileId);
        
        AIProfile aiProfile = aiProfileRepository.findById(profileId)
            .orElseThrow(() -> new ResourceNotFoundException("AI Profile not found with id: " + profileId));
        
        aiProfile.setAiAttributes(aiAttributes);
        aiProfile = aiProfileRepository.save(aiProfile);
        
        log.info("Updated AI profile {} attributes", profileId);
        
        return aiProfileMapper.toDto(aiProfile);
    }
    
    /**
     * Delete AI profile
     */
    @Transactional
    public void deleteProfile(UUID profileId) {
        log.debug("Deleting AI profile: {}", profileId);
        
        if (!aiProfileRepository.existsById(profileId)) {
            throw new ResourceNotFoundException("AI Profile not found with id: " + profileId);
        }
        
        aiProfileRepository.deleteById(profileId);
        
        log.info("Deleted AI profile {}", profileId);
    }
}
