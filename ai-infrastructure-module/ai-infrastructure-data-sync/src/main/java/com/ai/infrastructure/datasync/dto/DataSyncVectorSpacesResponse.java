package com.ai.infrastructure.datasync.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response listing available vector spaces (configured entity types).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncVectorSpacesResponse {

    private Boolean success;

    private String message;

    private List<String> vectorSpaces;
}

