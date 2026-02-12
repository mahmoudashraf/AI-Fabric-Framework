package com.ai.infrastructure.datasync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Batch sync request containing multiple operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncBatchRequest {

    @Valid
    @NotNull
    private DataSyncTrace trace;

    @Valid
    @NotEmpty
    private List<DataSyncOperation> operations;
}

