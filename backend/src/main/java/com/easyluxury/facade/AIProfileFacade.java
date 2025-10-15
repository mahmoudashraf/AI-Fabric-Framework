package com.easyluxury.facade;

import com.easyluxury.dto.AIProfileDataDto;
import com.easyluxury.dto.AIProfileDto;
import com.easyluxury.dto.GenerateProfileRequest;
import com.easyluxury.entity.User;
import com.easyluxury.service.AIProfileService;
import com.easyluxury.service.AIService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIProfileFacade {
    
    private final AIService aiService;
    private final AIProfileService aiProfileService;
    private final ObjectMapper objectMapper;
    
    /**
     * Generate AI profile from CV content
     */
    @Transactional
    public AIProfileDto generateProfileFromCV(User user, GenerateProfileRequest request) {
        log.info("Generating AI profile for user {} from CV content", user.getId());
        
        // Validate CV content
        String cvContent = request.getCvContent();
        if (cvContent == null || cvContent.trim().isEmpty()) {
            throw new IllegalArgumentException("CV content cannot be empty");
        }
        
        // Generate profile data using AI
        AIProfileDataDto profileData = aiService.generateProfileFromCV(cvContent);
        
        // Convert profile data to JSON string
        String aiAttributesJson;
        try {
            aiAttributesJson = objectMapper.writeValueAsString(profileData);
        } catch (JsonProcessingException e) {
            log.error("Error serializing AI profile data", e);
            throw new RuntimeException("Error processing AI profile data", e);
        }
        
        // Create and save AI profile
        AIProfileDto aiProfileDto = aiProfileService.createProfile(user, aiAttributesJson, null);
        
        log.info("Successfully generated AI profile {} for user {}", aiProfileDto.getId(), user.getId());
        
        return aiProfileDto;
    }
    
    /**
     * Get AI profile by ID
     */
    @Transactional(readOnly = true)
    public AIProfileDto getProfileById(UUID profileId) {
        return aiProfileService.getProfileById(profileId);
    }
    
    /**
     * Get latest AI profile for user
     */
    @Transactional(readOnly = true)
    public AIProfileDto getLatestProfileForUser(User user) {
        return aiProfileService.getLatestProfileForUser(user);
    }
    
    /**
     * Get all profiles for user
     */
    @Transactional(readOnly = true)
    public List<AIProfileDto> getAllProfilesForUser(User user) {
        return aiProfileService.getAllProfilesForUser(user);
    }
    
    /**
     * Parse AI attributes JSON to DTO
     */
    public AIProfileDataDto parseAiAttributes(String aiAttributesJson) {
        try {
            return objectMapper.readValue(aiAttributesJson, AIProfileDataDto.class);
        } catch (JsonProcessingException e) {
            log.error("Error parsing AI attributes JSON", e);
            throw new RuntimeException("Error parsing AI profile data", e);
        }
    }
}
