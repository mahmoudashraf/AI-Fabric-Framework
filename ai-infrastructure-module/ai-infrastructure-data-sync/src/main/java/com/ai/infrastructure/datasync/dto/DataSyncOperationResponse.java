package com.ai.infrastructure.datasync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of a single data sync operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncOperationResponse {

    private Boolean success;

    private String errorCode;

    private String message;

    private DataSyncOperationType type;

    private String vectorSpace;

    private String id;

    /**
     * Vector ID assigned by the vector database (UPSERT only).
     */
    private String vectorId;

    private Long processingTimeMs;

    private List<String> warnings;

    private Map<String, Object> metadata;
}

