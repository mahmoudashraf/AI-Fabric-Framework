package com.ai.fabric.vectorization.model;

import com.ai.fabric.integration.discovery.DiscoveryCountMethod;

import java.util.Map;

public record VectorizationDiscoveryResult(
    Map<String, Long> countsByEntityType,
    Map<String, DiscoveryCountMethod> countMethodByEntityType
) {
}
