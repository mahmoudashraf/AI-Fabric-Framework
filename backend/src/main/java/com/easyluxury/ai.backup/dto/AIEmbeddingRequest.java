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
 * AI Embedding Request DTO
 * 
 * Represents a request for generating embeddings from text input.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for AI embedding generation")
public class AIEmbeddingRequest {

    @NotBlank(message = "Input text cannot be blank")
    @Size(max = 8000, message = "Input text cannot exceed 8000 characters")
    @Schema(description = "The input text for embedding generation", example = "Luxury watch with Swiss movement", maxLength = 8000)
    private String input;

    @Schema(description = "Model to use for embedding generation", example = "text-embedding-3-small")
    private String model;

    @Schema(description = "Dimensions for the embedding", example = "1536", minimum = "1", maximum = "3072")
    private Integer dimensions;

    @Schema(description = "User identifier for tracking", example = "user123")
    private String userId;

    @Schema(description = "Request timeout in seconds", example = "30")
    @Builder.Default
    private Integer timeoutSeconds = 30;

    @Schema(description = "Additional context or metadata for embedding")
    private Map<String, Object> context;

    @Schema(description = "Batch processing flag", example = "false")
    @Builder.Default
    private Boolean batch = false;

    @Schema(description = "Normalize embeddings", example = "true")
    @Builder.Default
    private Boolean normalize = true;

    @Schema(description = "Input type", example = "text")
    private String inputType;

    @Schema(description = "Multiple inputs for batch processing")
    private List<String> inputs;
}