package com.easyluxury.service.impl;

import com.easyluxury.dto.AIProfileDto;
import com.easyluxury.entity.AIProfile;
import com.easyluxury.entity.User;
import com.easyluxury.mapper.AIProfileMapper;
import com.easyluxury.repository.AIProfileRepository;
import com.easyluxury.repository.UserRepository;
import com.easyluxury.service.AIProfileService;
import com.easyluxury.service.MinIOService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIProfileServiceImpl implements AIProfileService {
    
    private final AIProfileRepository aiProfileRepository;
    private final UserRepository userRepository;
    private final MinIOService minIOService;
    private final ObjectMapper objectMapper;
    
    @Override
    @Transactional
    public AIProfileDto createAIProfile(User user, String cvContent) {
        try {
            // Generate AI profile from CV content
            AIProfileDto aiProfileDto = generateProfileFromCV(cvContent);
            String aiAttributes = aiProfileDto.getAiAttributes();
            
            // Create AI profile entity
            AIProfile aiProfile = AIProfile.builder()
                    .user(user)
                    .aiAttributes(aiAttributes)
                    .status(AIProfile.AIProfileStatus.DRAFT)
                    .build();
            
            AIProfile savedProfile = aiProfileRepository.save(aiProfile);
            return AIProfileMapper.INSTANCE.toDto(savedProfile);
            
        } catch (Exception e) {
            log.error("Failed to create AI profile for user {}: {}", user.getId(), e.getMessage());
            throw new RuntimeException("Failed to create AI profile", e);
        }
    }
    
    @Override
    public AIProfileDto getAIProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        AIProfile aiProfile = aiProfileRepository.findByUserAndStatus(user, AIProfile.AIProfileStatus.COMPLETE)
                .orElse(aiProfileRepository.findByUserAndStatus(user, AIProfile.AIProfileStatus.PHOTOS_PENDING)
                        .orElse(aiProfileRepository.findByUserAndStatus(user, AIProfile.AIProfileStatus.DRAFT)
                                .orElseThrow(() -> new RuntimeException("No AI profile found"))));
        
        return AIProfileMapper.INSTANCE.toDto(aiProfile);
    }
    
    @Override
    @Transactional
    public AIProfileDto updateAIProfile(UUID profileId, String aiAttributes) {
        AIProfile aiProfile = aiProfileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("AI profile not found"));
        
        aiProfile.setAiAttributes(aiAttributes);
        AIProfile savedProfile = aiProfileRepository.save(aiProfile);
        
        return AIProfileMapper.INSTANCE.toDto(savedProfile);
    }
    
    @Override
    @Transactional
    public AIProfileDto enrichWithPhotos(UUID profileId, Map<String, String> photoUrls) {
        AIProfile aiProfile = aiProfileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("AI profile not found"));
        
        try {
            // Parse existing AI attributes
            JsonNode aiData = objectMapper.readTree(aiProfile.getAiAttributes());
            
            // Update photos with actual URLs
            JsonNode photos = aiData.get("photos");
            if (photos != null) {
                for (Map.Entry<String, String> entry : photoUrls.entrySet()) {
                    String photoType = entry.getKey();
                    String photoUrl = entry.getValue();
                    
                    if (photos.has(photoType)) {
                        if (photos.get(photoType).isArray()) {
                            // Handle array of photos
                            for (int i = 0; i < photos.get(photoType).size(); i++) {
                                String currentPhoto = photos.get(photoType).get(i).asText();
                                if (currentPhoto.startsWith("placeholder://")) {
                                    ((com.fasterxml.jackson.databind.node.ObjectNode) photos).put(photoType, photoUrl);
                                    break;
                                }
                            }
                        } else {
                            // Handle single photo
                            ((com.fasterxml.jackson.databind.node.ObjectNode) photos).put(photoType, photoUrl);
                        }
                    }
                }
            }
            
            // Update AI attributes
            aiProfile.setAiAttributes(objectMapper.writeValueAsString(aiData));
            aiProfile.setStatus(AIProfile.AIProfileStatus.COMPLETE);
            
            AIProfile savedProfile = aiProfileRepository.save(aiProfile);
            return AIProfileMapper.INSTANCE.toDto(savedProfile);
            
        } catch (Exception e) {
            log.error("Failed to enrich AI profile with photos: {}", e.getMessage());
            throw new RuntimeException("Failed to enrich profile with photos", e);
        }
    }
    
    @Override
    @Transactional
    public AIProfileDto uploadCV(UUID userId, MultipartFile cv) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        try {
            // Upload CV to MinIO
            String cvUrl = minIOService.uploadCV(userId, cv);
            
            // Create or update AI profile
            AIProfile aiProfile = aiProfileRepository.findByUserAndStatus(user, AIProfile.AIProfileStatus.DRAFT)
                    .orElse(AIProfile.builder()
                            .user(user)
                            .status(AIProfile.AIProfileStatus.DRAFT)
                            .build());
            
            aiProfile.setCvFileUrl(cvUrl);
            AIProfile savedProfile = aiProfileRepository.save(aiProfile);
            
            return AIProfileMapper.INSTANCE.toDto(savedProfile);
            
        } catch (Exception e) {
            log.error("Failed to upload CV for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to upload CV", e);
        }
    }
    
    @Override
    @Transactional
    public AIProfileDto uploadPhoto(UUID userId, String photoType, MultipartFile photo) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        try {
            // Upload photo to MinIO
            String photoUrl = minIOService.uploadPhoto(userId, photo, photoType);
            
            // Get or create AI profile
            AIProfile aiProfile = aiProfileRepository.findByUserAndStatus(user, AIProfile.AIProfileStatus.DRAFT)
                    .orElse(aiProfileRepository.findByUserAndStatus(user, AIProfile.AIProfileStatus.PHOTOS_PENDING)
                            .orElse(AIProfile.builder()
                                    .user(user)
                                    .status(AIProfile.AIProfileStatus.PHOTOS_PENDING)
                                    .build()));
            
            // Update AI attributes with photo URL
            Map<String, String> photoUrls = Map.of(photoType, photoUrl);
            return enrichWithPhotos(aiProfile.getId(), photoUrls);
            
        } catch (Exception e) {
            log.error("Failed to upload photo for user {}: {}", userId, e.getMessage());
            throw new RuntimeException("Failed to upload photo", e);
        }
    }
    
    @Override
    public List<AIProfileDto> getUserAIProfiles(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<AIProfile> profiles = aiProfileRepository.findByUserOrderByCreatedAtDesc(user);
        return profiles.stream()
                .map(AIProfileMapper.INSTANCE::toDto)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void deleteAIProfile(UUID profileId) {
        AIProfile aiProfile = aiProfileRepository.findById(profileId)
                .orElseThrow(() -> new RuntimeException("AI profile not found"));
        
        // Delete associated files from MinIO
        if (aiProfile.getCvFileUrl() != null) {
            minIOService.deleteCV(aiProfile.getCvFileUrl());
        }
        
        // Delete photos from MinIO
        try {
            JsonNode aiData = objectMapper.readTree(aiProfile.getAiAttributes());
            JsonNode photos = aiData.get("photos");
            if (photos != null) {
                photos.fieldNames().forEachRemaining(photoType -> {
                    JsonNode photoNode = photos.get(photoType);
                    if (photoNode.isArray()) {
                        photoNode.forEach(photo -> {
                            String photoUrl = photo.asText();
                            if (!photoUrl.startsWith("placeholder://")) {
                                minIOService.deletePhoto(photoUrl);
                            }
                        });
                    } else {
                        String photoUrl = photoNode.asText();
                        if (!photoUrl.startsWith("placeholder://")) {
                            minIOService.deletePhoto(photoUrl);
                        }
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Failed to delete photos from MinIO: {}", e.getMessage());
        }
        
        aiProfileRepository.delete(aiProfile);
    }
    
    @Override
    public AIProfileDto generateProfileFromCV(String cvContent) {
        // This is a mock implementation - replace with actual AI service integration
        try {
            String aiAttributes = generateMockAIProfile(cvContent);
            return AIProfileDto.builder()
                    .aiAttributes(aiAttributes)
                    .status(AIProfile.AIProfileStatus.DRAFT)
                    .build();
        } catch (Exception e) {
            log.error("Failed to generate AI profile from CV: {}", e.getMessage());
            throw new RuntimeException("Failed to generate AI profile", e);
        }
    }
    
    private String generateMockAIProfile(String cvContent) {
        try {
            // Mock AI profile with photo placeholders
            String mockProfile = """
                {
                    "name": "John Doe",
                    "jobTitle": "Senior Software Engineer",
                    "companies": [
                        {
                            "name": "Google",
                            "icon": "https://logo.clearbit.com/google.com",
                            "position": "Senior Software Engineer",
                            "duration": "2020-2024"
                        }
                    ],
                    "profileSummary": "Experienced software engineer with 5+ years of experience in full-stack development, specializing in React, Node.js, and cloud technologies.",
                    "skills": ["React", "Node.js", "Python", "AWS", "Docker"],
                    "experience": 5,
                    "photos": {
                        "profilePhoto": "placeholder://profile-photo",
                        "coverPhoto": "placeholder://cover-photo",
                        "professional": ["placeholder://professional-1", "placeholder://professional-2"],
                        "team": ["placeholder://team-1"],
                        "project": ["placeholder://project-1"]
                    },
                    "photoSuggestions": {
                        "profilePhoto": {
                            "required": true,
                            "count": 1,
                            "suggestions": ["Professional headshot for LinkedIn profile"],
                            "description": "A clear, professional headshot that represents your personal brand"
                        },
                        "professional": {
                            "required": false,
                            "count": 2,
                            "suggestions": ["Team meeting photo", "Conference presentation"],
                            "description": "Photos showcasing your professional work environment"
                        },
                        "team": {
                            "required": false,
                            "count": 1,
                            "suggestions": ["Team collaboration photo"],
                            "description": "Photos highlighting your teamwork and collaboration skills"
                        },
                        "project": {
                            "required": false,
                            "count": 1,
                            "suggestions": ["Project showcase or award ceremony"],
                            "description": "Photos demonstrating your achievements and project outcomes"
                        }
                    }
                }
                """;
            
            return mockProfile;
        } catch (Exception e) {
            log.error("Failed to generate mock AI profile: {}", e.getMessage());
            throw new RuntimeException("Failed to generate AI profile", e);
        }
    }
}
