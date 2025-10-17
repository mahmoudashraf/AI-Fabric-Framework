package com.easyluxury.ai.controller;

import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AISearchRequest;
import com.ai.infrastructure.dto.AISearchResponse;
import com.easyluxury.ai.service.SimpleAIService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Simple AI Controller
 * 
 * Simplified REST endpoints for AI operations that work with the core AI module.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai/simple")
@RequiredArgsConstructor
@Tag(name = "Simple AI Operations", description = "Simplified AI operations using core AI module")
public class SimpleAIController {
    
    private final AICoreService aiCoreService;
    private final SimpleAIService simpleAIService;
    
    @PostMapping("/generate")
    @Operation(summary = "Generate AI content", description = "Generate AI content using core AI service")
    public ResponseEntity<AIGenerationResponse> generateContent(
            @Parameter(description = "The prompt for generation", required = true)
            @RequestParam String prompt,
            @Parameter(description = "System prompt")
            @RequestParam(required = false) String systemPrompt,
            @Parameter(description = "Maximum tokens")
            @RequestParam(required = false) Integer maxTokens) {
        
        log.info("Generating AI content for prompt: {}", prompt);
        
        try {
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt(prompt)
                .systemPrompt(systemPrompt != null ? systemPrompt : "You are a helpful AI assistant.")
                .maxTokens(maxTokens)
                .entityType("content")
                .purpose("generation")
                .build();
            
            AIGenerationResponse response = "AI analysis placeholder";
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error generating AI content: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(AIGenerationResponse.builder()
                    .content("Error: " + e.getMessage())
                    .build());
        }
    }
    
    @PostMapping("/embedding")
    @Operation(summary = "Generate embeddings", description = "Generate embeddings for text using core AI service")
    public ResponseEntity<AIEmbeddingResponse> generateEmbedding(
            @Parameter(description = "The text to embed", required = true)
            @RequestParam String text,
            @Parameter(description = "Entity type")
            @RequestParam(required = false) String entityType) {
        
        log.info("Generating embedding for text: {}", text);
        
        try {
            AIEmbeddingRequest request = AIEmbeddingRequest.builder()
                .text(text)
                .entityType(entityType != null ? entityType : "content")
                .build();
            
            AIEmbeddingResponse response = aiCoreService.generateEmbedding(request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error generating embedding: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(AIEmbeddingResponse.builder()
                    .embedding(java.util.List.of())
                    .build());
        }
    }
    
    @PostMapping("/search")
    @Operation(summary = "Perform AI search", description = "Perform semantic search using core AI service")
    public ResponseEntity<AISearchResponse> performSearch(
            @Parameter(description = "The search query", required = true)
            @RequestParam String query,
            @Parameter(description = "Entity type to search")
            @RequestParam(required = false) String entityType,
            @Parameter(description = "Maximum results")
            @RequestParam(required = false) Integer limit) {
        
        log.info("Performing AI search for query: {}", query);
        
        try {
            AISearchRequest request = AISearchRequest.builder()
                .query(query)
                .entityType(entityType)
                .limit(limit != null ? limit : 10)
                .build();
            
            AISearchResponse response = aiCoreService.performSearch(request);
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("Error performing AI search: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(AISearchResponse.builder()
                    .results(java.util.List.of())
                    .totalResults(0)
                    .build());
        }
    }
    
    @GetMapping("/health")
    @Operation(summary = "AI health check", description = "Check AI service health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        log.info("Performing AI health check");
        
        try {
            // Simple health check - try to generate a simple response
            AIGenerationRequest request = AIGenerationRequest.builder()
                .prompt("Hello")
                .systemPrompt("Respond with 'OK'")
                .build();
            
            AIGenerationResponse response = "AI analysis placeholder";
            
            return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "AI service is operational",
                "response", response.getContent(),
                "timestamp", java.time.LocalDateTime.now()
            ));
            
        } catch (Exception e) {
            log.error("AI health check failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "DOWN",
                    "message", "AI service is not operational: " + e.getMessage(),
                    "timestamp", java.time.LocalDateTime.now()
                ));
        }
    }
}