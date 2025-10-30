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
 * AI Search Response DTO
 * 
 * Represents the response from AI-powered search operations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from AI-powered search")
public class AISearchResponse {

    @Schema(description = "Search results")
    private List<SearchResult> results;

    @Schema(description = "Total number of results found", example = "150")
    private Long totalResults;

    @Schema(description = "Number of results returned", example = "10")
    private Integer returnedResults;

    @Schema(description = "Search query used", example = "luxury watches under $5000")
    private String query;

    @Schema(description = "Search index used", example = "products")
    private String index;

    @Schema(description = "Search timestamp")
    private LocalDateTime timestamp;

    @Schema(description = "Search metadata")
    private Map<String, Object> metadata;

    @Schema(description = "Search ID for tracking", example = "search_123456789")
    private String searchId;

    @Schema(description = "Request ID for correlation", example = "req_123456789")
    private String requestId;

    @Schema(description = "Search status", example = "SUCCESS")
    private String status;

    @Schema(description = "Error message if search failed")
    private String errorMessage;

    @Schema(description = "Processing time in milliseconds", example = "450")
    private Long processingTimeMs;

    @Schema(description = "Search facets")
    private Map<String, Object> facets;

    @Schema(description = "Search suggestions")
    private List<String> suggestions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Individual search result")
    public static class SearchResult {
        
        @Schema(description = "Result ID", example = "prod_123")
        private String id;

        @Schema(description = "Result content or text", example = "Luxury Swiss Watch with Automatic Movement")
        private String content;

        @Schema(description = "Result title", example = "Premium Swiss Watch")
        private String title;

        @Schema(description = "Similarity score", example = "0.95")
        private Double score;

        @Schema(description = "Result metadata")
        private Map<String, Object> metadata;

        @Schema(description = "Result highlights")
        private List<String> highlights;

        @Schema(description = "Result type", example = "product")
        private String type;

        @Schema(description = "Result URL or reference", example = "/products/123")
        private String url;
    }
}