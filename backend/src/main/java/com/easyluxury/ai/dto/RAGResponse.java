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
 * RAG (Retrieval-Augmented Generation) Response DTO
 * 
 * Represents the response from RAG operations with generated content and source documents.
 * 
 * @author AI Infrastructure Team
 * @version 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from RAG (Retrieval-Augmented Generation) operations")
public class RAGResponse {

    @Schema(description = "Generated answer to the question", example = "Based on our luxury watch collection, here are the best options under $5000...")
    private String answer;

    @Schema(description = "Source documents used for generation")
    private List<SourceDocument> sources;

    @Schema(description = "Original question", example = "What are the best luxury watches under $5000?")
    private String question;

    @Schema(description = "Knowledge base used", example = "products")
    private String knowledgeBase;

    @Schema(description = "Generation timestamp")
    private LocalDateTime timestamp;

    @Schema(description = "RAG metadata")
    private Map<String, Object> metadata;

    @Schema(description = "RAG ID for tracking", example = "rag_123456789")
    private String ragId;

    @Schema(description = "Request ID for correlation", example = "req_123456789")
    private String requestId;

    @Schema(description = "RAG status", example = "SUCCESS")
    private String status;

    @Schema(description = "Error message if RAG failed")
    private String errorMessage;

    @Schema(description = "Processing time in milliseconds", example = "2500")
    private Long processingTimeMs;

    @Schema(description = "Confidence score for the answer", example = "0.85", minimum = "0.0", maximum = "1.0")
    private Double confidence;

    @Schema(description = "Number of documents retrieved", example = "5")
    private Integer documentsRetrieved;

    @Schema(description = "Number of documents used", example = "3")
    private Integer documentsUsed;

    @Schema(description = "Generation model used", example = "gpt-4o-mini")
    private String model;

    @Schema(description = "Usage information")
    private UsageInfo usage;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Source document used in RAG")
    public static class SourceDocument {
        
        @Schema(description = "Document ID", example = "doc_123")
        private String id;

        @Schema(description = "Document title", example = "Luxury Watch Collection")
        private String title;

        @Schema(description = "Document content snippet", example = "Our luxury watch collection features...")
        private String content;

        @Schema(description = "Similarity score", example = "0.92")
        private Double score;

        @Schema(description = "Document metadata")
        private Map<String, Object> metadata;

        @Schema(description = "Document URL or reference", example = "/products/luxury-watches")
        private String url;

        @Schema(description = "Document type", example = "product")
        private String type;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Usage information for the RAG operation")
    public static class UsageInfo {
        
        @Schema(description = "Number of tokens used for retrieval", example = "150")
        private Integer retrievalTokens;

        @Schema(description = "Number of tokens used for generation", example = "300")
        private Integer generationTokens;

        @Schema(description = "Total tokens used", example = "450")
        private Integer totalTokens;

        @Schema(description = "Cost in USD", example = "0.0035")
        private Double costUsd;

        @Schema(description = "Processing time in milliseconds", example = "2500")
        private Long processingTimeMs;
    }
}