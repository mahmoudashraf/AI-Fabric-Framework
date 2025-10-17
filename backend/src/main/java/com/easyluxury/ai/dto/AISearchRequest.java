package com.easyluxury.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * AI Search Request DTO
 * 
 * Represents a request for AI-powered search operations.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for AI-powered search")
public class AISearchRequest {

    @NotBlank(message = "Query cannot be blank")
    @Size(max = 1000, message = "Query cannot exceed 1000 characters")
    @Schema(description = "The search query", example = "luxury watches under $5000", maxLength = 1000)
    private String query;

    @Schema(description = "Search index or collection", example = "products")
    private String index;

    @Schema(description = "Maximum number of results to return", example = "10", minimum = "1", maximum = "100")
    @Builder.Default
    private Integer limit = 10;

    @Schema(description = "Search offset for pagination", example = "0", minimum = "0")
    @Builder.Default
    private Integer offset = 0;

    @Schema(description = "Search filters")
    private Map<String, Object> filters;

    @Schema(description = "Search sort criteria")
    private List<SortCriteria> sort;

    @Schema(description = "Search facets to return")
    private List<String> facets;

    @Schema(description = "User identifier for personalization", example = "user123")
    private String userId;

    @Schema(description = "Request timeout in seconds", example = "30")
    @Builder.Default
    private Integer timeoutSeconds = 30;

    @Schema(description = "Search context or metadata")
    private Map<String, Object> context;

    @Schema(description = "Search type", example = "semantic")
    private String searchType;

    @Schema(description = "Similarity threshold", example = "0.7", minimum = "0.0", maximum = "1.0")
    @Builder.Default
    private Double similarityThreshold = 0.7;

    @Schema(description = "Include metadata in results", example = "true")
    @Builder.Default
    private Boolean includeMetadata = true;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Sort criteria for search results")
    public static class SortCriteria {
        
        @Schema(description = "Field to sort by", example = "price")
        private String field;

        @Schema(description = "Sort direction", example = "asc", allowableValues = {"asc", "desc"})
        private String direction;

        @Schema(description = "Sort priority", example = "1")
        private Integer priority;
    }
}