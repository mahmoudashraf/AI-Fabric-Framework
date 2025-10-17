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
 * RAG (Retrieval-Augmented Generation) Request DTO
 * 
 * Represents a request for RAG operations combining retrieval and generation.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request for RAG (Retrieval-Augmented Generation) operations")
public class RAGRequest {

    @NotBlank(message = "Question cannot be blank")
    @Size(max = 2000, message = "Question cannot exceed 2000 characters")
    @Schema(description = "The question or query for RAG", example = "What are the best luxury watches under $5000?", maxLength = 2000)
    private String question;

    @Schema(description = "Knowledge base or index to search", example = "products")
    private String knowledgeBase;

    @Schema(description = "Maximum number of relevant documents to retrieve", example = "5", minimum = "1", maximum = "20")
    @Builder.Default
    private Integer maxDocuments = 5;

    @Schema(description = "Similarity threshold for document retrieval", example = "0.7", minimum = "0.0", maximum = "1.0")
    @Builder.Default
    private Double similarityThreshold = 0.7;

    @Schema(description = "Maximum tokens for the generated response", example = "1000", minimum = "100", maximum = "4000")
    @Builder.Default
    private Integer maxTokens = 1000;

    @Schema(description = "Temperature for response generation", example = "0.7", minimum = "0.0", maximum = "2.0")
    @Builder.Default
    private Double temperature = 0.7;

    @Schema(description = "User identifier for personalization", example = "user123")
    private String userId;

    @Schema(description = "Request timeout in seconds", example = "60")
    @Builder.Default
    private Integer timeoutSeconds = 60;

    @Schema(description = "Additional context for RAG", example = "Focus on Swiss-made watches")
    private String context;

    @Schema(description = "RAG configuration parameters")
    private Map<String, Object> config;

    @Schema(description = "Include source documents in response", example = "true")
    @Builder.Default
    private Boolean includeSources = true;

    @Schema(description = "RAG mode", example = "hybrid", allowableValues = {"retrieval", "generation", "hybrid"})
    @Builder.Default
    private String mode = "hybrid";

    @Schema(description = "Language for the response", example = "en")
    private String language;

    @Schema(description = "Response format", example = "text", allowableValues = {"text", "json", "markdown"})
    @Builder.Default
    private String format = "text";

    @Schema(description = "Custom instructions for the AI", example = "Provide detailed technical specifications")
    private String instructions;
}