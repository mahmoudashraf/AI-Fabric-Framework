package com.ai.fabric.platform.backend.deployment.service;

public record CoolifyCreatePostgresDatabaseRequest(
    String projectUuid,
    String serverUuid,
    String environmentName,
    String environmentUuid,
    String destinationUuid,
    String name,
    String description,
    String image,
    String postgresUser,
    String postgresPassword,
    String postgresDatabase,
    boolean isPublic,
    boolean instantDeploy
) {
}
