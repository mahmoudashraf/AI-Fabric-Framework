package com.ai.behavior.api;

import com.ai.behavior.api.dto.BehaviorEventResponse;
import com.ai.behavior.api.dto.BehaviorQueryRequest;
import com.ai.behavior.service.BehaviorQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ai-behavior/query")
@RequiredArgsConstructor
public class BehaviorQueryController {

    private final BehaviorQueryService queryService;
    private final BehaviorEventMapper mapper;

    @PostMapping
    public ResponseEntity<List<BehaviorEventResponse>> query(@Valid @RequestBody BehaviorQueryRequest request) {
        var events = request.getProviderType() == null
            ? queryService.query(request.toQuery())
            : queryService.query(request.getProviderType(), request.toQuery());
        var responses = events.stream()
            .map(mapper::toResponse)
            .toList();
        return ResponseEntity.ok(responses);
    }
}
