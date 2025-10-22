package com.easyluxury.facade;

import com.easyluxury.dto.AIProfileDataDto;
import com.easyluxury.dto.AIProfileDto;
import com.easyluxury.dto.GenerateProfileRequest;
import com.easyluxury.entity.User;
import com.easyluxury.entity.AIProfileStatus;
import com.easyluxury.service.AIProfileService;
import com.easyluxury.service.AIService;
import com.easyluxury.service.S3Service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIProfileFacade {
    
    private final AIService aiService;
    private final AIProfileService aiProfileService;
    private final S3Service s3Service;
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
     * Upload CV file and generate profile
     */
    @Transactional
    public AIProfileDto uploadAndGenerateProfile(User user, MultipartFile file) {
        log.info("Uploading CV file and generating profile for user {}", user.getId());
        
        // Validate file
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("CV file cannot be empty");
        }
        
        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("application/pdf") && 
            !contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") &&
            !contentType.equals("application/msword") &&
            !contentType.equals("text/plain"))) {
            throw new IllegalArgumentException("Only PDF, Word documents, and text files are supported");
        }
        
        // Extract text from file
        String cvContent;
        try {
            cvContent = extractTextFromFile(file);
        } catch (IOException e) {
            log.error("Error extracting text from CV file", e);
            throw new RuntimeException("Failed to read CV file", e);
        }
        
        // Upload file to S3
        String fileKey = s3Service.generateFileKey("cv-files", file.getOriginalFilename());
        String fileUrl;
        try {
            fileUrl = s3Service.uploadFile(fileKey, file.getBytes(), contentType);
        } catch (IOException e) {
            log.error("Error uploading CV file to S3", e);
            throw new RuntimeException("Failed to upload CV file", e);
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
        
        // Create and save AI profile with CV file URL
        AIProfileDto aiProfileDto = aiProfileService.createProfile(user, aiAttributesJson, fileUrl);
        
        log.info("Successfully generated AI profile {} for user {}", aiProfileDto.getId(), user.getId());
        
        return aiProfileDto;
    }
    
    /**
     * Upload photo for AI profile
     */
    @Transactional
    public AIProfileDto uploadPhoto(UUID profileId, MultipartFile file, String photoType) {
        log.info("Uploading {} photo for profile {}", photoType, profileId);
        
        // Validate file
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Photo file cannot be empty");
        }
        
        // Validate file type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are supported");
        }
        
        // Get existing profile
        AIProfileDto profile = aiProfileService.getProfileById(profileId);
        
        // Parse current AI attributes
        AIProfileDataDto profileData = parseAiAttributes(profile.getAiAttributes());
        
        // Upload photo to S3
        String fileKey = s3Service.generateFileKey("profile-photos", file.getOriginalFilename());
        String photoUrl;
        try {
            photoUrl = s3Service.uploadFile(fileKey, file.getBytes(), contentType);
        } catch (IOException e) {
            log.error("Error uploading photo to S3", e);
            throw new RuntimeException("Failed to upload photo", e);
        }
        
        // Update photos in profile data
        if (profileData.getPhotos() == null) {
            profileData.setPhotos(new HashMap<>());
        }
        
        Map<String, Object> photos = profileData.getPhotos();
        
        // Handle different photo types
        switch (photoType.toLowerCase()) {
            case "profilephoto":
            case "profile":
                photos.put("profilePhoto", photoUrl);
                break;
            case "coverphoto":
            case "cover":
                photos.put("coverPhoto", photoUrl);
                break;
            case "professional":
                @SuppressWarnings("unchecked")
                List<String> professionalPhotos = (List<String>) photos.getOrDefault("professional", new java.util.ArrayList<String>());
                professionalPhotos.add(photoUrl);
                photos.put("professional", professionalPhotos);
                break;
            case "team":
                @SuppressWarnings("unchecked")
                List<String> teamPhotos = (List<String>) photos.getOrDefault("team", new java.util.ArrayList<String>());
                teamPhotos.add(photoUrl);
                photos.put("team", teamPhotos);
                break;
            case "project":
                @SuppressWarnings("unchecked")
                List<String> projectPhotos = (List<String>) photos.getOrDefault("project", new java.util.ArrayList<String>());
                projectPhotos.add(photoUrl);
                photos.put("project", projectPhotos);
                break;
            default:
                throw new IllegalArgumentException("Invalid photo type: " + photoType);
        }
        
        // Convert updated profile data to JSON
        String updatedAiAttributes;
        try {
            updatedAiAttributes = objectMapper.writeValueAsString(profileData);
        } catch (JsonProcessingException e) {
            log.error("Error serializing updated AI profile data", e);
            throw new RuntimeException("Error processing AI profile data", e);
        }
        
        // Update profile with new photo
        AIProfileDto updatedProfile = aiProfileService.updateAiAttributes(profileId, updatedAiAttributes);
        
        log.info("Successfully uploaded {} photo for profile {}", photoType, profileId);
        
        return updatedProfile;
    }
    
    /**
     * Publish AI profile
     */
    @Transactional
    public AIProfileDto publishProfile(UUID profileId) {
        log.info("Publishing AI profile {}", profileId);
        
        // Update status to COMPLETE
        AIProfileDto profile = aiProfileService.updateProfileStatus(profileId, AIProfileStatus.COMPLETE);
        
        log.info("Successfully published AI profile {}", profileId);
        
        return profile;
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
    
    /**
     * Extract text content from PDF or Word file
     */
    private String extractTextFromFile(MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        
        if ("application/pdf".equals(contentType)) {
            // Extract text from PDF
            try (PDDocument document = PDDocument.load(file.getInputStream())) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        } else if ("application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType) ||
                   "application/msword".equals(contentType)) {
            // Extract text from Word document
            try (XWPFDocument document = new XWPFDocument(file.getInputStream());
                 XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                return extractor.getText();
            }
        } else if ("text/plain".equals(contentType)) {
            // Extract text from plain text file
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                return content.toString();
            }
        } else {
            throw new IllegalArgumentException("Unsupported file type: " + contentType);
        }
    }
}
