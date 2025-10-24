package com.ai.infrastructure.controller;

import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.ai.infrastructure.entity.Behavior;
import com.ai.infrastructure.service.BehaviorService;
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
 * Generic Behavior Controller
 * 
 * REST controller for behavioral data operations.
 * This controller is domain-agnostic and can be used across different applications.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/ai/behaviors")
@RequiredArgsConstructor
@Slf4j
public class BehaviorController {
    
    private final BehaviorService behaviorService;
    
    /**
     * Create a new behavior record
     */
    @PostMapping
    public ResponseEntity<BehaviorResponse> createBehavior(@RequestBody BehaviorRequest request) {
        log.info("Creating behavior for user: {}", request.getUserId());
        
        try {
            BehaviorResponse response = behaviorService.createBehavior(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating behavior: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get behavior by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<BehaviorResponse> getBehaviorById(@PathVariable UUID id) {
        log.info("Getting behavior by ID: {}", id);
        
        try {
            BehaviorResponse response = behaviorService.getBehaviorById(id);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Behavior not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error getting behavior: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get behaviors by user ID
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BehaviorResponse>> getBehaviorsByUserId(@PathVariable UUID userId) {
        log.info("Getting behaviors for user: {}", userId);
        
        try {
            List<BehaviorResponse> responses = behaviorService.getBehaviorsByUserId(userId);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting behaviors for user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get behaviors by user ID with pagination
     */
    @GetMapping("/user/{userId}/page")
    public ResponseEntity<Page<BehaviorResponse>> getBehaviorsByUserId(
            @PathVariable UUID userId,
            Pageable pageable) {
        log.info("Getting behaviors for user: {} with pagination", userId);
        
        try {
            Page<BehaviorResponse> responses = behaviorService.getBehaviorsByUserId(userId, pageable);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting behaviors for user with pagination: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get behaviors by behavior type
     */
    @GetMapping("/type/{behaviorType}")
    public ResponseEntity<List<BehaviorResponse>> getBehaviorsByType(@PathVariable String behaviorType) {
        log.info("Getting behaviors by type: {}", behaviorType);
        
        try {
            List<BehaviorResponse> responses = behaviorService.getBehaviorsByType(Behavior.BehaviorType.valueOf(behaviorType));
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            log.error("Invalid behavior type: {}", behaviorType);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error getting behaviors by type: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get behaviors by behavior type with pagination
     */
    @GetMapping("/type/{behaviorType}/page")
    public ResponseEntity<Page<BehaviorResponse>> getBehaviorsByType(
            @PathVariable String behaviorType,
            Pageable pageable) {
        log.info("Getting behaviors by type: {} with pagination", behaviorType);
        
        try {
            Page<BehaviorResponse> responses = behaviorService.getBehaviorsByType(Behavior.BehaviorType.valueOf(behaviorType), pageable);
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            log.error("Invalid behavior type: {}", behaviorType);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error getting behaviors by type with pagination: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get behaviors by user ID and behavior type
     */
    @GetMapping("/user/{userId}/type/{behaviorType}")
    public ResponseEntity<List<BehaviorResponse>> getBehaviorsByUserIdAndType(
            @PathVariable UUID userId,
            @PathVariable String behaviorType) {
        log.info("Getting behaviors for user: {} and type: {}", userId, behaviorType);
        
        try {
            List<BehaviorResponse> responses = behaviorService.getBehaviorsByUserIdAndType(userId, Behavior.BehaviorType.valueOf(behaviorType));
            return ResponseEntity.ok(responses);
        } catch (IllegalArgumentException e) {
            log.error("Invalid behavior type: {}", behaviorType);
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error getting behaviors for user and type: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get behaviors by entity type and entity ID
     */
    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<List<BehaviorResponse>> getBehaviorsByEntity(
            @PathVariable String entityType,
            @PathVariable String entityId) {
        log.info("Getting behaviors for entity: {} - {}", entityType, entityId);
        
        try {
            List<BehaviorResponse> responses = behaviorService.getBehaviorsByEntity(entityType, entityId);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting behaviors for entity: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get behaviors by session ID
     */
    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<BehaviorResponse>> getBehaviorsBySession(@PathVariable String sessionId) {
        log.info("Getting behaviors for session: {}", sessionId);
        
        try {
            List<BehaviorResponse> responses = behaviorService.getBehaviorsBySession(sessionId);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting behaviors for session: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get behaviors by date range
     */
    @GetMapping("/date-range")
    public ResponseEntity<List<BehaviorResponse>> getBehaviorsByDateRange(
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {
        log.info("Getting behaviors from {} to {}", startDate, endDate);
        
        try {
            List<BehaviorResponse> responses = behaviorService.getBehaviorsByDateRange(startDate, endDate);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting behaviors by date range: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get behaviors by user ID and date range
     */
    @GetMapping("/user/{userId}/date-range")
    public ResponseEntity<List<BehaviorResponse>> getBehaviorsByUserIdAndDateRange(
            @PathVariable UUID userId,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate) {
        log.info("Getting behaviors for user: {} from {} to {}", userId, startDate, endDate);
        
        try {
            List<BehaviorResponse> responses = behaviorService.getBehaviorsByUserIdAndDateRange(userId, startDate, endDate);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting behaviors for user and date range: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Get behaviors by user ID and date range with pagination
     */
    @GetMapping("/user/{userId}/date-range/page")
    public ResponseEntity<Page<BehaviorResponse>> getBehaviorsByUserIdAndDateRange(
            @PathVariable UUID userId,
            @RequestParam LocalDateTime startDate,
            @RequestParam LocalDateTime endDate,
            Pageable pageable) {
        log.info("Getting behaviors for user: {} from {} to {} with pagination", userId, startDate, endDate);
        
        try {
            Page<BehaviorResponse> responses = behaviorService.getBehaviorsByUserIdAndDateRange(userId, startDate, endDate, pageable);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            log.error("Error getting behaviors for user and date range with pagination: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Analyze behaviors for a user
     */
    @GetMapping("/user/{userId}/analyze")
    public ResponseEntity<BehaviorAnalysisResult> analyzeBehaviors(@PathVariable UUID userId) {
        log.info("Analyzing behaviors for user: {}", userId);
        
        try {
            BehaviorAnalysisResult result = behaviorService.analyzeBehaviors(userId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error analyzing behaviors for user: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Update behavior
     */
    @PutMapping("/{id}")
    public ResponseEntity<BehaviorResponse> updateBehavior(
            @PathVariable UUID id,
            @RequestBody BehaviorRequest request) {
        log.info("Updating behavior: {}", id);
        
        try {
            BehaviorResponse response = behaviorService.updateBehavior(id, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("Behavior not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error updating behavior: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    /**
     * Delete behavior
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBehavior(@PathVariable UUID id) {
        log.info("Deleting behavior: {}", id);
        
        try {
            behaviorService.deleteBehavior(id);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            log.error("Behavior not found: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error deleting behavior: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}