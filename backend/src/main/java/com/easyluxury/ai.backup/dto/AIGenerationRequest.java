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

import java.util.Map;

/**
 * AI Generation Request DTO
 * 
 * Represents a request for AI content generation with customizable parameters.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for AI content generation")
public class AIGenerationRequest {

    @NotBlank(message = "Prompt cannot be blank")
    @Size(max = 4000, message = "Prompt cannot exceed 4000 characters")
    @Schema(description = "The input prompt for AI generation", example = "Generate a product description for a luxury watch", maxLength = 4000)
    private String prompt;

    @Schema(description = "Maximum number of tokens to generate", example = "1000", minimum = "1", maximum = "4000")
    @Builder.Default
    private Integer maxTokens = 1000;

    @Schema(description = "Temperature for generation randomness", example = "0.7", minimum = "0.0", maximum = "2.0")
    @Builder.Default
    private Double temperature = 0.7;

    @Schema(description = "Top-p sampling parameter", example = "0.9", minimum = "0.0", maximum = "1.0")
    @Builder.Default
    private Double topP = 0.9;

    @Schema(description = "Frequency penalty", example = "0.0", minimum = "-2.0", maximum = "2.0")
    @Builder.Default
    private Double frequencyPenalty = 0.0;

    @Schema(description = "Presence penalty", example = "0.0", minimum = "-2.0", maximum = "2.0")
    @Builder.Default
    private Double presencePenalty = 0.0;

    @Schema(description = "Stop sequences for generation", example = "[\"\\n\\n\", \"Human:\"]")
    private String[] stop;

    @Schema(description = "Additional context or metadata for generation")
    private Map<String, Object> context;

    @Schema(description = "Model to use for generation", example = "gpt-4o-mini")
    private String model;

    @Schema(description = "Stream response", example = "false")
    @Builder.Default
    private Boolean stream = false;

    @Schema(description = "User identifier for tracking", example = "user123")
    private String userId;

    @Schema(description = "Request timeout in seconds", example = "30")
    @Builder.Default
    private Integer timeoutSeconds = 30;
}