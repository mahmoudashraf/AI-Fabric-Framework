package com.ai.infrastructure.controller;

import com.ai.infrastructure.dto.AIProfileRequest;
import com.ai.infrastructure.dto.AIProfileResponse;
import com.ai.infrastructure.entity.AIProfile;
import com.ai.infrastructure.service.AIProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Generic AI Profile Controller
 * 
 * REST controller for AI profile operations.
 * This controller is domain-agnostic and can be used across different applications.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/ai/profiles")
@RequiredArgsConstructor
@Slf4j
public class AIProfileController {
    
    private final AIProfileService aiProfileService;
    
    /**
     * Create a new AI profile
     */
    @PostMapping
    public ResponseEntity<AIProfileResponse> createAIProfile(@RequestBody AIProfileRequest request) {
        log.info("Creating AI profile for user: {}", request.getUserId());
        
        try {
            AIProfileResponse response = aiProfileService.createAIProfile(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating AI profile: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get AI profile by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<AIProfileResponse> getAIProfileById(@PathVariable UUID id) {
        log.info("Getting AI profile by ID: {}", id);
        
        try {
            AIProfileResponse response = aiProfileService.getAIProfileById(id);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("AI Profile not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting AI profile: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get AI profile by user ID
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<AIProfileResponse> getAIProfileByUserId(@PathVariable UUID userId) {
        log.info("Getting AI profile for user: {}", userId);
        
        try {
            AIProfileResponse response = aiProfileService.getAIProfileByUserId(userId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("AI Profile not found for user: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting AI profile for user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get AI profiles by status
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<AIProfileResponse>> getAIProfilesByStatus(@PathVariable String status) {
        log.info("Getting AI profiles by status: {}", status);
        
        try {
            List<AIProfileResponse> responses = aiProfileService.getAIProfilesByStatus(AIProfile.AIProfileStatus.valueOf(status));
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            log.error("Invalid status: {}", status);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error getting AI profiles by status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get AI profiles by status with pagination
     */
    @GetMapping("/status/{status}/page")
    public ResponseEntity<Page<AIProfileResponse>> getAIProfilesByStatus(
            @PathVariable String status,
            Pageable pageable) {
        log.info("Getting AI profiles by status: {} with pagination", status);
        
        try {
            Page<AIProfileResponse> responses = aiProfileService.getAIProfilesByStatus(AIProfile.AIProfileStatus.valueOf(status), pageable);
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            log.error("Invalid status: {}", status);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error getting AI profiles by status with pagination: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get AI profiles by user ID and status
     */
    @GetMapping("/user/{userId}/status/{status}")
    public ResponseEntity<List<AIProfileResponse>> getAIProfilesByUserIdAndStatus(
            @PathVariable UUID userId,
            @PathVariable String status) {
        log.info("Getting AI profiles for user: {} and status: {}", userId, status);
        
        try {
            List<AIProfileResponse> responses = aiProfileService.getAIProfilesByUserIdAndStatus(userId, AIProfile.AIProfileStatus.valueOf(status));
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            log.error("Invalid status: {}", status);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error getting AI profiles for user and status: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get AI profiles by confidence score range
     */
    @GetMapping("/confidence-score")
    public ResponseEntity<List<AIProfileResponse>> getAIProfilesByConfidenceScoreRange(
            @RequestParam Double minScore,
            @RequestParam Double maxScore) {
        log.info("Getting AI profiles by confidence score range: {} - {}", minScore, maxScore);
        
        try {
            List<AIProfileResponse> responses = aiProfileService.getAIProfilesByConfidenceScoreRange(minScore, maxScore);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting AI profiles by confidence score range: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get AI profiles by version
     */
    @GetMapping("/version/{version}")
    public ResponseEntity<List<AIProfileResponse>> getAIProfilesByVersion(@PathVariable Integer version) {
        log.info("Getting AI profiles by version: {}", version);
        
        try {
            List<AIProfileResponse> responses = aiProfileService.getAIProfilesByVersion(version);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting AI profiles by version: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get AI profiles by user ID and version
     */
    @GetMapping("/user/{userId}/version/{version}")
    public ResponseEntity<List<AIProfileResponse>> getAIProfilesByUserIdAndVersion(
            @PathVariable UUID userId,
            @PathVariable Integer version) {
        log.info("Getting AI profiles for user: {} and version: {}", userId, version);
        
        try {
            List<AIProfileResponse> responses = aiProfileService.getAIProfilesByUserIdAndVersion(userId, version);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting AI profiles for user and version: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get AI profiles by date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<List<AIProfileResponse>> getAIProfilesByDateRange(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {
        log.info("Getting AI profiles from {} to {}", startDate, endDate);
        
        try {
            List<AIProfileResponse> responses = aiProfileService.getAIProfilesByDateRange(startDate, endDate);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting AI profiles by date range: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get AI profiles by user ID and date range
     */
    @GetMapping("/user/{userId}/date-range")
    public ResponseEntity<List<AIProfileResponse>> getAIProfilesByUserIdAndDateRange(
            @PathVariable UUID userId,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {
        log.info("Getting AI profiles for user: {} from {} to {}", userId, startDate, endDate);
        
        try {
            List<AIProfileResponse> responses = aiProfileService.getAIProfilesByUserIdAndDateRange(userId, startDate, endDate);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting AI profiles for user and date range: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get AI profiles by user ID and date range with pagination
     */
    @GetMapping("/user/{userId}/date-range/page")
    public ResponseEntity<Page<AIProfileResponse>> getAIProfilesByUserIdAndDateRange(
            @PathVariable UUID userId,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate,
            Pageable pageable) {
        log.info("Getting AI profiles for user: {} from {} to {} with pagination", userId, startDate, endDate);
        
        try {
            Page<AIProfileResponse> responses = aiProfileService.getAIProfilesByUserIdAndDateRange(userId, startDate, endDate, pageable);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting AI profiles for user and date range with pagination: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get latest AI profile by user ID
     */
    @GetMapping("/user/{userId}/latest")
    public ResponseEntity<AIProfileResponse> getLatestAIProfileByUserId(@PathVariable UUID userId) {
        log.info("Getting latest AI profile for user: {}", userId);
        
        try {
            AIProfileResponse response = aiProfileService.getLatestAIProfileByUserId(userId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("No AI Profile found for user: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting latest AI profile for user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Update AI profile
     */
    @PutMapping("/{id}")
    public ResponseEntity<AIProfileResponse> updateAIProfile(
            @PathVariable UUID id,
            @RequestBody AIProfileRequest request) {
        log.info("Updating AI profile: {}", id);
        
        try {
            AIProfileResponse response = aiProfileService.updateAIProfile(id, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("AI Profile not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating AI profile: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Update AI profile by user ID
     */
    @PutMapping("/user/{userId}")
    public ResponseEntity<AIProfileResponse> updateAIProfileByUserId(
            @PathVariable UUID userId,
            @RequestBody AIProfileRequest request) {
        log.info("Updating AI profile for user: {}", userId);
        
        try {
            AIProfileResponse response = aiProfileService.updateAIProfileByUserId(userId, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("AI Profile not found for user: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating AI profile for user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Delete AI profile
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAIProfile(@PathVariable UUID id) {
        log.info("Deleting AI profile: {}", id);
        
        try {
            aiProfileService.deleteAIProfile(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("AI Profile not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting AI profile: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Delete AI profile by user ID
     */
    @DeleteMapping("/user/{userId}")
    public ResponseEntity<Void> deleteAIProfileByUserId(@PathVariable UUID userId) {
        log.info("Deleting AI profile for user: {}", userId);
        
        try {
            aiProfileService.deleteAIProfileByUserId(userId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("AI Profile not found for user: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting AI profile for user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}