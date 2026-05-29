package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;

public record CoolifyDatabaseSummary(
    String uuid,
    String name,
    String status,
    String databaseType,
    String postgresUser,
    String postgresDatabase,
    JsonNode raw
) {
}
