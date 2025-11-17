package com.ai.behavior.api;

import com.ai.behavior.api.dto.BehaviorBatchIngestionRequest;
import com.ai.behavior.api.dto.BehaviorEventRequest;
import com.ai.behavior.api.dto.BehaviorIngestionResponse;
import com.ai.behavior.ingestion.BehaviorIngestionService;
import com.ai.behavior.model.BehaviorEvent;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-behavior/ingest")
@RequiredArgsConstructor
public class BehaviorIngestionController {

    private final BehaviorIngestionService ingestionService;

    @PostMapping("/event")
    public ResponseEntity<BehaviorIngestionResponse> ingest(@Valid @RequestBody BehaviorEventRequest request) {
        BehaviorEvent event = ingestionService.ingest(request.toEvent());
        return ResponseEntity.accepted().body(BehaviorIngestionResponse.accepted(event.getId()));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> ingestBatch(@Valid @RequestBody BehaviorBatchIngestionRequest request) {
        List<BehaviorEvent> events = request.getEvents().stream()
            .map(BehaviorEventRequest::toEvent)
            .toList();
        ingestionService.ingestBatch(events);
        return ResponseEntity.accepted().body(Map.of(
            "status", "accepted",
            "count", events.size()
        ));
    }
}
