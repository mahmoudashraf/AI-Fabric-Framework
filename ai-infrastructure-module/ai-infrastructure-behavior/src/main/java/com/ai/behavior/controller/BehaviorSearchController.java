package com.ai.behavior.controller;

import com.ai.behavior.dto.OrchestratedQueryRequest;
import com.ai.behavior.dto.OrchestratedSearchResponse;
import com.ai.behavior.service.BehaviorQueryOrchestrator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/behavior/search")
public class BehaviorSearchController {

    private final BehaviorQueryOrchestrator queryOrchestrator;

    @PostMapping("/orchestrated")
    public ResponseEntity<OrchestratedSearchResponse> orchestratedSearch(
        @Valid @RequestBody OrchestratedQueryRequest request
    ) {
        OrchestratedSearchResponse response = queryOrchestrator.executeQuery(request);
        HttpStatus status = StringUtils.hasText(response.getError()) ? HttpStatus.BAD_REQUEST : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}
