package com.ai.infrastructure.datasync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Batch sync response with per-operation results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncBatchResponse {

    private Boolean success;

    private String errorCode;

    private String message;

    private Integer totalOperations;

    private Integer succeededOperations;

    private Integer failedOperations;

    private List<DataSyncOperationResponse> results;
}

