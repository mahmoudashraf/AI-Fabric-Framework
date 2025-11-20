package com.ai.behavior.controller;

import com.ai.behavior.dto.BehaviorEventBatchRequest;
import com.ai.behavior.dto.BehaviorEventRequest;
import com.ai.behavior.model.BehaviorEventEntity;
import com.ai.behavior.service.BehaviorEventIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/behavior/events")
public class BehaviorEventController {

    private final BehaviorEventIngestionService ingestionService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> ingestSingle(@Valid @RequestBody BehaviorEventRequest request) {
        BehaviorEventEntity entity = toEntity(request);
        BehaviorEventEntity saved = ingestionService.ingestSingleEvent(entity);
        return ResponseEntity.accepted().body(Map.of("eventId", saved.getId()));
    }

    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> ingestBatch(@Valid @RequestBody BehaviorEventBatchRequest request) {
        List<BehaviorEventEntity> entities = request.getEvents().stream()
            .map(this::toEntity)
            .collect(Collectors.toList());
        List<BehaviorEventEntity> saved = ingestionService.ingestBatchEvents(entities);
        List<UUID> ids = saved.stream().map(BehaviorEventEntity::getId).toList();
        return ResponseEntity.accepted().body(Map.of(
            "count", saved.size(),
            "eventIds", ids
        ));
    }

    private BehaviorEventEntity toEntity(BehaviorEventRequest request) {
        return BehaviorEventEntity.builder()
            .userId(request.getUserId())
            .eventType(request.getEventType())
            .eventData(request.getEventData())
            .source(request.getSource())
            .build();
    }
}
