package com.ai.infrastructure.datasync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Single operation inside a batch sync request.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncOperation {

    @NotNull
    private DataSyncOperationType type;

    @NotBlank
    private String vectorSpace;

    @NotBlank
    private String id;

    /**
     * Pre-normalized text content for UPSERT operations.
     */
    private String content;

    /**
     * Optional raw entity payload for UPSERT operations.
     */
    private Map<String, Object> entity;

    /**
     * Optional metadata to store alongside the vector.
     */
    private Map<String, Object> metadata;
}

