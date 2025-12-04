package com.ai.behavior.api;

import com.ai.behavior.api.dto.BehaviorAlertResponse;
import com.ai.behavior.api.dto.BehaviorHealthResponse;
import com.ai.behavior.service.BehaviorMonitoringService;
import com.ai.behavior.storage.BehaviorAlertRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai-behavior/monitoring")
public class BehaviorMonitoringController {

    private final BehaviorMonitoringService monitoringService;
    private final BehaviorAlertRepository alertRepository;

    public BehaviorMonitoringController(BehaviorMonitoringService monitoringService,
                                        BehaviorAlertRepository alertRepository) {
        this.monitoringService = monitoringService;
        this.alertRepository = alertRepository;
    }

    @GetMapping("/health")
    public ResponseEntity<BehaviorHealthResponse> health() {
        return ResponseEntity.ok(monitoringService.health());
    }

    @GetMapping("/users/{userId}/alerts")
    public ResponseEntity<List<BehaviorAlertResponse>> alerts(@PathVariable UUID userId) {
        return ResponseEntity.ok(
            alertRepository.findByUserIdOrderByDetectedAtDesc(userId).stream()
                .map(BehaviorAlertResponse::from)
                .toList()
        );
    }
}
