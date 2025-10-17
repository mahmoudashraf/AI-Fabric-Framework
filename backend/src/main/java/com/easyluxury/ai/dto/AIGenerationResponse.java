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
 * AI Generation Response DTO
 * 
 * Represents the response from AI content generation with metadata and usage information.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from AI content generation")
public class AIGenerationResponse {

    @Schema(description = "Generated content", example = "This luxury watch features a premium Swiss movement...")
    private String content;

    @Schema(description = "Model used for generation", example = "gpt-4o-mini")
    private String model;

    @Schema(description = "Generation timestamp")
    private LocalDateTime timestamp;

    @Schema(description = "Usage information")
    private UsageInfo usage;

    @Schema(description = "Generation metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Generation ID for tracking", example = "gen_123456789")
    private String generationId;

    @Schema(description = "Request ID for correlation", example = "req_123456789")
    private String requestId;

    @Schema(description = "Generation status", example = "SUCCESS")
    private String status;

    @Schema(description = "Error message if generation failed")
    private String errorMessage;

    @Schema(description = "Processing time in milliseconds", example = "1250")
    private Long processingTimeMs;

    @Schema(description = "Alternative generations if available")
    private List<String> alternatives;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Usage information for the generation")
    public static class UsageInfo {
        
        @Schema(description = "Number of prompt tokens used", example = "150")
        private Integer promptTokens;

        @Schema(description = "Number of completion tokens generated", example = "250")
        private Integer completionTokens;

        @Schema(description = "Total tokens used", example = "400")
        private Integer totalTokens;

        @Schema(description = "Cost in USD", example = "0.0025")
        private Double costUsd;

        @Schema(description = "Processing time in milliseconds", example = "1250")
        private Long processingTimeMs;
    }
}