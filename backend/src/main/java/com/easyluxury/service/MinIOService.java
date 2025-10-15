package com.easyluxury.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface MinIOService {
    
    String uploadPhoto(UUID userId, MultipartFile photo, String photoType);
    
    String uploadCV(UUID userId, MultipartFile cv);
    
    void deletePhoto(String photoUrl);
    
    void deleteCV(String cvUrl);
    
    String generatePresignedUrl(String objectKey);
    
    boolean photoExists(String photoUrl);
}
