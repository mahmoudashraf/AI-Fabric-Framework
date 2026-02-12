package com.ai.infrastructure.datasync.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Upsert request for syncing a record into a vector space.
 *
 * <p>Provide either {@link #content} (pre-normalized) OR {@link #entity} (raw fields to normalize).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncUpsertRequest {

    @NotBlank
    private String vectorSpace;

    @NotBlank
    private String id;

    /**
     * Pre-normalized text content to embed and index.
     */
    private String content;

    /**
     * Optional raw entity payload. When {@link #content} is blank, the server will normalize this using
     * {@code ai-entity-config.yml} for the corresponding vector space.
     */
    private Map<String, Object> entity;

    /**
     * Optional metadata to store alongside the vector.
     */
    private Map<String, Object> metadata;

    @Valid
    @NotNull
    private DataSyncTrace trace;
}

