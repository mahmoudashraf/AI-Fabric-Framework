package com.ai.fabric.platform.backend.deployment.model;

import java.util.Map;

public record DeploymentPocImportRecordRequest(
    String id,
    String content,
    Map<String, Object> entity,
    Map<String, Object> metadata
) {
}
