package com.ai.fabric.vectorization.model;

import java.util.Map;

public record VectorizationMappedRecord(
    String entityType,
    String logicalEntityId,
    String sourceRecordId,
    String sourceRecordVersion,
    Map<String, Object> entity,
    Map<String, Object> metadata
) {
}
