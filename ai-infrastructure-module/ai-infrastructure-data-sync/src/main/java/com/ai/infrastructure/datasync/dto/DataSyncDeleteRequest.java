package com.ai.infrastructure.datasync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Delete request for removing a record from a vector space.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncDeleteRequest {

    @NotBlank
    private String vectorSpace;

    @NotBlank
    private String id;

    @Valid
    @NotNull
    private DataSyncTrace trace;
}

