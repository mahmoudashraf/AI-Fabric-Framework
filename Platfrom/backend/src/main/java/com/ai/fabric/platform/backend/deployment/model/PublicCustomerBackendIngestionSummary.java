package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;
import java.util.Map;

public record PublicCustomerBackendIngestionSummary(
    boolean configured,
    boolean available,
    List<String> entityTypes,
    Map<String, List<String>> operationsByEntityType,
    String batchUrl,
    String workStatusUrlTemplate,
    String readinessUrl,
    List<String> requiredScopes,
    String guidance
) {
}
