package com.ai.fabric.platform.backend.marketplace.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record MarketplaceMcpImportDraftRequest(
    @NotBlank @Size(max = 128) String pluginId,
    @NotBlank @Size(max = 128) String pluginSlug,
    @NotBlank @Size(max = 255) String displayName,
    @Size(max = 2000) String description,
    @NotBlank @Size(max = 128) String version,
    @NotBlank @Size(max = 128) String serverRef,
    Map<String, Object> server,
    Map<String, Object> trace,
    List<String> allowedTools,
    @Size(max = 128) String gatewayServiceRef,
    @Size(max = 128) String publisherSlug,
    @Size(max = 255) String publisherDisplayName
) {
}
