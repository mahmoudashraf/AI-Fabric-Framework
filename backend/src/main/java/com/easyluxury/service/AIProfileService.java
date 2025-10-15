package com.easyluxury.service;

import com.easyluxury.dto.AIProfileDto;
import com.easyluxury.entity.AIProfile;
import com.easyluxury.entity.User;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface AIProfileService {
    
    AIProfileDto createAIProfile(User user, String cvContent);
    
    AIProfileDto getAIProfile(UUID userId);
    
    AIProfileDto updateAIProfile(UUID profileId, String aiAttributes);
    
    AIProfileDto enrichWithPhotos(UUID profileId, Map<String, String> photoUrls);
    
    AIProfileDto uploadCV(UUID userId, MultipartFile cv);
    
    AIProfileDto uploadPhoto(UUID userId, String photoType, MultipartFile photo);
    
    List<AIProfileDto> getUserAIProfiles(UUID userId);
    
    void deleteAIProfile(UUID profileId);
    
    AIProfileDto generateProfileFromCV(String cvContent);
}
