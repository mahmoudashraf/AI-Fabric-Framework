package com.easyluxury.controller;

import com.easyluxury.dto.AIProfileDto;
import com.easyluxury.service.AIProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/ai-profile")
@RequiredArgsConstructor
@Tag(name = "AI Profile", description = "AI Profile management endpoints")
public class AIProfileController {
    
    private final AIProfileService aiProfileService;
    
    @PostMapping("/generate")
    @Operation(summary = "Generate AI profile from CV content")
    public ResponseEntity<AIProfileDto> generateProfile(@RequestBody Map<String, String> request) {
        try {
            String cvContent = request.get("cvContent");
            if (cvContent == null || cvContent.trim().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            // For now, we'll use a mock user - in real implementation, get from authentication
            // User user = getCurrentUser(authentication);
            // AIProfileDto result = aiProfileService.createAIProfile(user, cvContent);
            
            AIProfileDto result = aiProfileService.generateProfileFromCV(cvContent);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("Failed to generate AI profile: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/user/{userId}")
    @Operation(summary = "Get AI profile for user")
    public ResponseEntity<AIProfileDto> getAIProfile(@PathVariable UUID userId) {
        try {
            AIProfileDto result = aiProfileService.getAIProfile(userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get AI profile for user {}: {}", userId, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
    
    @PutMapping("/{profileId}")
    @Operation(summary = "Update AI profile")
    public ResponseEntity<AIProfileDto> updateAIProfile(
            @PathVariable UUID profileId,
            @RequestBody Map<String, String> request) {
        try {
            String aiAttributes = request.get("aiAttributes");
            if (aiAttributes == null) {
                return ResponseEntity.badRequest().build();
            }
            
            AIProfileDto result = aiProfileService.updateAIProfile(profileId, aiAttributes);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to update AI profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/{profileId}/enrich-photos")
    @Operation(summary = "Enrich AI profile with photos")
    public ResponseEntity<AIProfileDto> enrichWithPhotos(
            @PathVariable UUID profileId,
            @RequestBody Map<String, String> photoUrls) {
        try {
            AIProfileDto result = aiProfileService.enrichWithPhotos(profileId, photoUrls);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to enrich AI profile {} with photos: {}", profileId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/upload-cv")
    @Operation(summary = "Upload CV file")
    public ResponseEntity<AIProfileDto> uploadCV(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") UUID userId) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            AIProfileDto result = aiProfileService.uploadCV(userId, file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to upload CV for user {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @PostMapping("/upload-photo")
    @Operation(summary = "Upload photo")
    public ResponseEntity<AIProfileDto> uploadPhoto(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") UUID userId,
            @RequestParam("photoType") String photoType) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }
            
            AIProfileDto result = aiProfileService.uploadPhoto(userId, photoType, file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to upload photo for user {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @GetMapping("/user/{userId}/profiles")
    @Operation(summary = "Get all AI profiles for user")
    public ResponseEntity<List<AIProfileDto>> getUserAIProfiles(@PathVariable UUID userId) {
        try {
            List<AIProfileDto> result = aiProfileService.getUserAIProfiles(userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get AI profiles for user {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @DeleteMapping("/{profileId}")
    @Operation(summary = "Delete AI profile")
    public ResponseEntity<Void> deleteAIProfile(@PathVariable UUID profileId) {
        try {
            aiProfileService.deleteAIProfile(profileId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Failed to delete AI profile {}: {}", profileId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
