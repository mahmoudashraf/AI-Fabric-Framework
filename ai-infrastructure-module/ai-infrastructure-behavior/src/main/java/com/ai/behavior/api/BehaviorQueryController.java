package com.ai.behavior.api;

import com.ai.behavior.api.dto.BehaviorSignalResponse;
import com.ai.behavior.api.dto.BehaviorQueryRequest;
import com.ai.behavior.service.BehaviorQueryService;
import com.ai.behavior.storage.BehaviorAlertRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai-behavior")
public class BehaviorQueryController {

    private final BehaviorQueryService queryService;

    public BehaviorQueryController(BehaviorQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<BehaviorSignalResponse> getEvent(@PathVariable UUID eventId) {
        return ResponseEntity.ok(BehaviorSignalResponse.from(queryService.getEvent(eventId)));
    }

    @GetMapping("/users/{userId}/events")
    public ResponseEntity<List<BehaviorSignalResponse>> getUserEvents(@PathVariable UUID userId,
                                                                     @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(
            queryService.recentEvents(userId, limit).stream()
                .map(BehaviorSignalResponse::from)
                .toList()
        );
    }

    @PostMapping("/users/{userId}/events/query")
    public ResponseEntity<List<BehaviorSignalResponse>> query(@PathVariable UUID userId,
                                                             @RequestBody BehaviorQueryRequest request) {
        return ResponseEntity.ok(
            queryService.query(userId, request.toQuery()).stream()
                .map(BehaviorSignalResponse::from)
                .toList()
        );
    }
}
