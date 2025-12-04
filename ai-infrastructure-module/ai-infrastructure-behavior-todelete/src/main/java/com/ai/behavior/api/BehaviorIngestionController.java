package com.ai.behavior.api;

import com.ai.behavior.api.dto.BehaviorBatchIngestionRequest;
import com.ai.behavior.api.dto.BehaviorSignalRequest;
import com.ai.behavior.api.dto.BehaviorIngestionResponse;
import com.ai.behavior.ingestion.BehaviorIngestionService;
import com.ai.behavior.model.BehaviorSignal;
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
@RequestMapping("/api/ai-behavior/signals")
@RequiredArgsConstructor
public class BehaviorIngestionController {

    private final BehaviorIngestionService ingestionService;

    @PostMapping
    public ResponseEntity<BehaviorIngestionResponse> ingest(@Valid @RequestBody BehaviorSignalRequest request) {
        BehaviorSignal signal = ingestionService.ingest(request.toEvent());
        return ResponseEntity.accepted().body(BehaviorIngestionResponse.accepted(signal.getId()));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> ingestBatch(@Valid @RequestBody BehaviorBatchIngestionRequest request) {
        List<BehaviorSignal> signals = request.getEvents().stream()
            .map(BehaviorSignalRequest::toEvent)
            .toList();
        ingestionService.ingestBatch(signals);
        return ResponseEntity.accepted().body(Map.of(
            "status", "accepted",
            "count", signals.size()
        ));
    }
}
