package com.ai.fabric.vectorization.model;

public record TargetConnectionDescriptor(
    String targetType,
    String baseUrl,
    String batchPath,
    String vectorSpacesPath,
    String authHeader,
    String apiKey
) {
}
