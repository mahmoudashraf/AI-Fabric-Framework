package com.ai.fabric.platform.backend.secret.model;

public record UpsertManagedPlatformSecretRequest(String value, String deploymentId, String targetProfileId) {
}
