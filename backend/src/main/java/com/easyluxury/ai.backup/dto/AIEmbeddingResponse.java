package com.easyluxury.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI Embedding Response DTO
 * 
 * Represents the response from AI embedding generation with vector data and metadata.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from AI embedding generation")
public class AIEmbeddingResponse {

    @Schema(description = "Generated embeddings as vectors")
    private List<List<Double>> embeddings;

    @Schema(description = "Model used for embedding generation", example = "text-embedding-3-small")
    private String model;

    @Schema(description = "Embedding dimensions", example = "1536")
    private Integer dimensions;

    @Schema(description = "Generation timestamp")
    private LocalDateTime timestamp;

    @Schema(description = "Usage information")
    private UsageInfo usage;

    @Schema(description = "Embedding metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Embedding ID for tracking", example = "emb_123456789")
    private String embeddingId;

    @Schema(description = "Request ID for correlation", example = "req_123456789")
    private String requestId;

    @Schema(description = "Generation status", example = "SUCCESS")
    private String status;

    @Schema(description = "Error message if generation failed")
    private String errorMessage;

    @Schema(description = "Processing time in milliseconds", example = "850")
    private Long processingTimeMs;

    @Schema(description = "Input text that was embedded")
    private String input;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Usage information for the embedding")
    public static class UsageInfo {
        
        @Schema(description = "Number of input tokens used", example = "25")
        private Integer inputTokens;

        @Schema(description = "Total tokens used", example = "25")
        private Integer totalTokens;

        @Schema(description = "Cost in USD", example = "0.0001")
        private Double costUsd;

        @Schema(description = "Processing time in milliseconds", example = "850")
        private Long processingTimeMs;

        @Schema(description = "Embedding dimensions", example = "1536")
        private Integer dimensions;
    }
}