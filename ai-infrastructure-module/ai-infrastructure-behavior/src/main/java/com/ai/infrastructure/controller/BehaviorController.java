package com.ai.infrastructure.controller;

import com.ai.infrastructure.dto.BehaviorAnalysisResult;
import com.ai.infrastructure.dto.BehaviorRequest;
import com.ai.infrastructure.dto.BehaviorResponse;
import com.ai.infrastructure.service.BehaviorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Legacy-compatible REST controller preserving the /api/ai/behaviors contract.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/behaviors")
@RequiredArgsConstructor
public class BehaviorController {

    private final BehaviorService behaviorService;

    @PostMapping
    public ResponseEntity<BehaviorResponse> createBehavior(@RequestBody BehaviorRequest request) {
        return ResponseEntity.ok(behaviorService.createBehavior(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<BehaviorResponse> getBehavior(@PathVariable UUID id) {
        return ResponseEntity.ok(behaviorService.getBehaviorById(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<BehaviorResponse>> getBehaviorsByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(behaviorService.getBehaviorsByUserId(userId));
    }

    @GetMapping("/user/{userId}/page")
    public ResponseEntity<Page<BehaviorResponse>> getBehaviorsByUserPaged(@PathVariable UUID userId, Pageable pageable) {
        return ResponseEntity.ok(behaviorService.getBehaviorsByUserId(userId, pageable));
    }

    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<List<BehaviorResponse>> getBehaviorsByEntity(@PathVariable String entityType,
                                                                       @PathVariable String entityId) {
        return ResponseEntity.ok(behaviorService.getBehaviorsByEntity(entityType, entityId));
    }

    @GetMapping("/session/{sessionId}")
    public ResponseEntity<List<BehaviorResponse>> getBehaviorsBySession(@PathVariable String sessionId) {
        return ResponseEntity.ok(behaviorService.getBehaviorsBySession(sessionId));
    }

    @GetMapping("/date-range")
    public ResponseEntity<List<BehaviorResponse>> getBehaviorsByDateRange(@RequestParam LocalDateTime startDate,
                                                                          @RequestParam LocalDateTime endDate) {
        return ResponseEntity.ok(behaviorService.getBehaviorsByDateRange(startDate, endDate));
    }

    @GetMapping("/user/{userId}/date-range")
    public ResponseEntity<List<BehaviorResponse>> getBehaviorsByUserDateRange(@PathVariable UUID userId,
                                                                              @RequestParam LocalDateTime startDate,
                                                                              @RequestParam LocalDateTime endDate) {
        return ResponseEntity.ok(behaviorService.getBehaviorsByUserIdAndDateRange(userId, startDate, endDate));
    }

    @GetMapping("/user/{userId}/type/{behaviorType}")
    public ResponseEntity<List<BehaviorResponse>> getBehaviorsByUserAndType(@PathVariable UUID userId,
                                                                            @PathVariable String behaviorType) {
        com.ai.infrastructure.entity.Behavior.BehaviorType type =
            com.ai.infrastructure.entity.Behavior.BehaviorType.valueOf(behaviorType.toUpperCase());
        return ResponseEntity.ok(behaviorService.getBehaviorsByUserIdAndType(userId, type));
    }

    @GetMapping("/user/{userId}/analyze")
    public ResponseEntity<BehaviorAnalysisResult> analyzeBehaviors(@PathVariable UUID userId) {
        return ResponseEntity.ok(behaviorService.analyzeBehaviors(userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBehavior(@PathVariable UUID id) {
        behaviorService.deleteBehavior(id);
        return ResponseEntity.noContent().build();
    }
}
