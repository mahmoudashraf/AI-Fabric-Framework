package com.ai.fabric.realapps.itsupport.controller;

import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/bot")
@RequiredArgsConstructor
@Slf4j
public class BotController {

    private final ObjectProvider<RAGOrchestrator> orchestratorProvider;
    private final ObjectProvider<ActionHandlerRegistry> actionHandlerRegistryProvider;

    @GetMapping("/actions")
    public ResponseEntity<Map<String, Object>> actions() {
        ActionHandlerRegistry registry = actionHandlerRegistryProvider.getIfAvailable();
        if (registry == null) {
            return ResponseEntity.ok(Map.of(
                "enabled", false,
                "reason", "ActionHandlerRegistry bean not available"
            ));
        }
        return ResponseEntity.ok(Map.of(
            "enabled", true,
            "actions", registry.getAllMetadata()
        ));
    }

    @PostMapping("/query")
    public ResponseEntity<OrchestrationResult> query(@RequestBody Map<String, Object> payload) {
        RAGOrchestrator orchestrator = orchestratorProvider.getIfAvailable();
        if (orchestrator == null) {
            return ResponseEntity.ok(OrchestrationResult.builder()
                .success(false)
                .message("Orchestrator not configured")
                .build());
        }

        String query = payload.get("query") != null ? Objects.toString(payload.get("query"), "") : "";
        String userId = payload.get("userId") != null ? Objects.toString(payload.get("userId"), null) : null;
        String sessionId = payload.get("sessionId") != null
            ? Objects.toString(payload.get("sessionId"), null)
            : UUID.randomUUID().toString();

        OrchestrationContext context = userId != null
            ? OrchestrationContext.builder().userId(userId).sessionId(sessionId).build()
            : OrchestrationContext.forSession(sessionId);

        return ResponseEntity.ok(orchestrator.orchestrate(query, context));
    }
}

