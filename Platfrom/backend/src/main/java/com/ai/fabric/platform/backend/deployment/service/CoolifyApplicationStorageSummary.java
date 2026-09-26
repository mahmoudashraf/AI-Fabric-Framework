package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;

record CoolifyApplicationStorageSummary(
    String uuid,
    String type,
    String name,
    String mountPath,
    String hostPath,
    boolean directory,
    JsonNode raw
) {
}
