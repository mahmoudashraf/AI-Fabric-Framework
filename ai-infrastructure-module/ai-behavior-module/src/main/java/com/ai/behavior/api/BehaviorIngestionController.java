package com.ai.behavior.api;

import com.ai.behavior.api.dto.BehaviorBatchIngestRequest;
import com.ai.behavior.api.dto.BehaviorEventRequest;
import com.ai.behavior.api.dto.BehaviorEventResponse;
import com.ai.behavior.ingestion.BehaviorIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/ai-behavior/ingest")
@RequiredArgsConstructor
public class BehaviorIngestionController {

    private final BehaviorIngestionService ingestionService;
    private final BehaviorEventMapper mapper;

    @PostMapping
    public ResponseEntity<BehaviorEventResponse> ingest(@Valid @RequestBody BehaviorEventRequest request) {
        var event = mapper.toEntity(request);
        var stored = ingestionService.ingest(event);
        return ResponseEntity.ok(mapper.toResponse(stored));
    }

    @PostMapping("/batch")
    public ResponseEntity<List<BehaviorEventResponse>> ingestBatch(@Valid @RequestBody BehaviorBatchIngestRequest request) {
        var events = request.getEvents().stream()
            .map(mapper::toEntity)
            .toList();
        var stored = ingestionService.ingestBatch(events);
        var responses = stored.stream()
            .map(mapper::toResponse)
            .toList();
        return ResponseEntity.ok(responses);
    }
}
