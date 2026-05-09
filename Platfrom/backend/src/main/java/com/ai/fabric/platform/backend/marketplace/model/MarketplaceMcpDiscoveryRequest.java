package com.ai.fabric.platform.backend.marketplace.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record MarketplaceMcpDiscoveryRequest(
    @NotBlank @Size(max = 128) String serverRef,
    Map<String, Object> server,
    Map<String, Object> trace,
    List<String> allowedTools,
    @Size(max = 128) String gatewayServiceRef
) {
}
