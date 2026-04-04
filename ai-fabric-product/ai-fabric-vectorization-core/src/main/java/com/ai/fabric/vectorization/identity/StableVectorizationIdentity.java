package com.ai.fabric.vectorization.identity;

import java.util.Locale;

public record StableVectorizationIdentity(
    String logicalEntityId,
    String sourceRecordId,
    String chunkId
) {

    public String effectiveTargetId() {
        if (chunkId == null || chunkId.isBlank()) {
            return logicalEntityId;
        }
        String normalizedChunk = chunkId.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9._-]+", "-")
            .replaceAll("-{2,}", "-")
            .replaceAll("(^-|-$)", "");
        return logicalEntityId + "::chunk:" + normalizedChunk;
    }
}
