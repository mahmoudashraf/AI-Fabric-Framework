package com.ai.fabric.vectorization.model;

public record VectorizationTargetWriteResult(
    int succeeded,
    int failed
) {
}
