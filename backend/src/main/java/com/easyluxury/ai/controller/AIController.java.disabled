package com.easyluxury.ai.controller;

import com.easyluxury.ai.dto.*;
import com.easyluxury.ai.facade.AIFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI Controller
 * 
 * REST endpoints for AI operations including generation, embeddings, search, and RAG.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Operations", description = "AI-powered operations including generation, embeddings, search, and RAG")
public class AIController {
    
    private final AIFacade aiFacade;
    
    // ==================== AI Generation Endpoints ====================
    
    @PostMapping("/generate")
    @Operation(
        summary = "Generate AI content",
        description = "Generate AI content using the specified prompt and parameters"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Content generated successfully",
            content = @Content(schema = @Schema(implementation = AIGenerationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AIGenerationResponse> generateContent(
            @Valid @RequestBody AIGenerationRequest request) {
        log.info("Received AI generation request: {}", request.getPrompt());
        
        AIGenerationResponse response = aiFacade.generateContent(request);
        
        if ("ERROR".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    // ==================== AI Embedding Endpoints ====================
    
    @PostMapping("/embeddings")
    @Operation(
        summary = "Generate AI embeddings",
        description = "Generate embeddings for the given input text"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Embeddings generated successfully",
            content = @Content(schema = @Schema(implementation = AIEmbeddingResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AIEmbeddingResponse> generateEmbeddings(
            @Valid @RequestBody AIEmbeddingRequest request) {
        log.info("Received AI embedding request for input: {}", request.getInput());
        
        AIEmbeddingResponse response = aiFacade.generateEmbeddings(request);
        
        if ("ERROR".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    // ==================== AI Search Endpoints ====================
    
    @PostMapping("/search")
    @Operation(
        summary = "Perform AI-powered search",
        description = "Search using AI semantic search capabilities"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search completed successfully",
            content = @Content(schema = @Schema(implementation = AISearchResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AISearchResponse> performSearch(
            @Valid @RequestBody AISearchRequest request) {
        log.info("Received AI search request with query: {}", request.getQuery());
        
        AISearchResponse response = aiFacade.performSearch(request);
        
        if ("ERROR".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    // ==================== RAG Endpoints ====================
    
    @PostMapping("/rag")
    @Operation(
        summary = "Perform RAG operation",
        description = "Perform Retrieval-Augmented Generation using knowledge base"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "RAG operation completed successfully",
            content = @Content(schema = @Schema(implementation = RAGResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<RAGResponse> performRAG(
            @Valid @RequestBody RAGRequest request) {
        log.info("Received RAG request with question: {}", request.getQuestion());
        
        RAGResponse response = aiFacade.performRAG(request);
        
        if ("ERROR".equals(response.getStatus())) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
        
        return ResponseEntity.ok(response);
    }
    
    // ==================== Quick Access Endpoints ====================
    
    @GetMapping("/generate/quick")
    @Operation(
        summary = "Quick AI content generation",
        description = "Generate AI content with minimal parameters"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Content generated successfully",
            content = @Content(schema = @Schema(implementation = AIGenerationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AIGenerationResponse> quickGenerate(
            @Parameter(description = "The prompt for generation", required = true)
            @RequestParam String prompt,
            @Parameter(description = "Maximum tokens to generate")
            @RequestParam(defaultValue = "500") Integer maxTokens,
            @Parameter(description = "Temperature for generation")
            @RequestParam(defaultValue = "0.7") Double temperature) {
        
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt(prompt)
            .maxTokens(maxTokens)
            .temperature(temperature)
            .build();
        
        return generateContent(request);
    }
    
    @GetMapping("/search/quick")
    @Operation(
        summary = "Quick AI search",
        description = "Perform AI search with minimal parameters"
    )
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Search completed successfully",
            content = @Content(schema = @Schema(implementation = AISearchResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Internal server error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<AISearchResponse> quickSearch(
            @Parameter(description = "The search query", required = true)
            @RequestParam String query,
            @Parameter(description = "Maximum results to return")
            @RequestParam(defaultValue = "10") Integer limit,
            @Parameter(description = "Search index")
            @RequestParam(required = false) String index) {
        
        AISearchRequest request = AISearchRequest.builder()
            .query(query)
            .limit(limit)
            .index(index)
            .build();
        
        return performSearch(request);
    }
    
    // ==================== Error Response DTO ====================
    
    @Schema(description = "Error response")
    public static class ErrorResponse {
        @Schema(description = "Error message")
        private String message;
        
        @Schema(description = "Error code")
        private String code;
        
        @Schema(description = "Timestamp")
        private String timestamp;
        
        // Constructors, getters, setters
        public ErrorResponse() {}
        
        public ErrorResponse(String message, String code) {
            this.message = message;
            this.code = code;
            this.timestamp = java.time.LocalDateTime.now().toString();
        }
        
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    }
}