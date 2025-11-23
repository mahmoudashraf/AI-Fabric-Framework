package com.ai.infrastructure.relationship.model;

import lombok.Builder;
import lombok.Data;

/**
 * Runtime options controlling how a relationship query should be executed.
 */
@Data
@Builder
public class QueryOptions {
    private QueryMode forceMode;
    private ReturnMode returnMode;
    private Double similarityThreshold;
    private Integer limit;
    @Builder.Default
    private boolean includeVectorScores = false;

    public static QueryOptions defaults() {
        return QueryOptions.builder()
            .returnMode(ReturnMode.IDS)
            .build();
    }
}
