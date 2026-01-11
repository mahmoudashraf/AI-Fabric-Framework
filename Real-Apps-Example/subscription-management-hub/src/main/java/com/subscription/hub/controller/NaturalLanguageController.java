package com.subscription.hub.controller;

import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Controller for natural language query interface
 * Integrates with AI Fabric Framework's RAGOrchestrator for intent extraction and handling
 */
@RestController
@RequestMapping("/api/subscriptions/query")
@RequiredArgsConstructor
@Slf4j
public class NaturalLanguageController {
    
    @Autowired(required = false)
    private RAGOrchestrator ragOrchestrator;
    
    @Autowired(required = false)
    private ActionHandlerRegistry actionHandlerRegistry;
    
    @PostMapping
    public ResponseEntity<OrchestrationResult> query(
            @RequestBody Map<String, Object> request) {
        
        String query = (String) request.get("query");
        String userId = request.get("userId") != null ? request.get("userId").toString() : null;
        String sessionId = request.get("sessionId") != null ? request.get("sessionId").toString() : UUID.randomUUID().toString();
        
        if (ragOrchestrator == null) {
            return ResponseEntity.status(503)
                .body(OrchestrationResult.builder()
                    .success(false)
                    .message("RAG Orchestrator is not available")
                    .build());
        }
        
        OrchestrationContext context = userId != null
            ? OrchestrationContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build()
            : OrchestrationContext.forSession(sessionId);
        
        OrchestrationResult result = ragOrchestrator.orchestrate(query, context);
        
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/actions/execute")
    public ResponseEntity<ActionResult> executeAction(
            @RequestBody Map<String, Object> request) {
        
        if (actionHandlerRegistry == null) {
            return ResponseEntity.status(503)
                .body(ActionResult.builder()
                    .success(false)
                    .message("Action Handler Registry is not available")
                    .build());
        }
        
        String action = (String) request.get("action");
        Map<String, Object> params = (Map<String, Object>) request.get("params");
        String userId = request.get("userId") != null ? request.get("userId").toString() : null;
        Boolean confirmed = (Boolean) request.getOrDefault("confirmed", false);
        
        if (!confirmed) {
            return ResponseEntity.badRequest()
                .body(ActionResult.builder()
                    .success(false)
                    .message("Action requires confirmation")
                    .build());
        }
        
        var handler = actionHandlerRegistry.findHandler(action);
        if (handler.isEmpty()) {
            return ResponseEntity.badRequest()
                .body(ActionResult.builder()
                    .success(false)
                    .message("Action handler not found: " + action)
                    .build());
        }
        
        ActionResult result = handler.get().executeAction(params, userId);
        return ResponseEntity.ok(result);
    }
}
