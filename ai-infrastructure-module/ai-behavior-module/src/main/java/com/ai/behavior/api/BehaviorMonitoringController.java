package com.ai.behavior.api;

import com.ai.behavior.ingestion.BehaviorEventSink;
import com.ai.behavior.repository.BehaviorEventRepository;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import com.ai.behavior.repository.BehaviorMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-behavior/monitoring")
@RequiredArgsConstructor
public class BehaviorMonitoringController {

    private final BehaviorEventRepository eventRepository;
    private final BehaviorInsightsRepository insightsRepository;
    private final BehaviorMetricsRepository metricsRepository;
    private final BehaviorEventSink eventSink;

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sinkType", eventSink.getSinkType());
        payload.put("events", eventRepository.count());
        payload.put("insights", insightsRepository.count());
        payload.put("metrics", metricsRepository.count());
        return ResponseEntity.ok(payload);
    }
}
