package com.ai.fabric.vectorization.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record VectorizationSourcePage(
    List<JsonNode> records,
    String nextCursor,
    boolean hasMore
) {
}
