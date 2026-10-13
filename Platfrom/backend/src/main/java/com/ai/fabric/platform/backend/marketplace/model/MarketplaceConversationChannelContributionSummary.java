package com.ai.fabric.platform.backend.marketplace.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record MarketplaceConversationChannelContributionSummary(
    String contractVersion,
    String bindingId,
    String adapterImplementationId,
    String adapterVersion,
    List<String> compatibleBehaviorTypes,
    List<String> capabilities,
    String providerBaseUrlField,
    String providerApiKeySecretRefField,
    String runtimeCallbackSigningSecretRefField,
    String providerWebhookSigningSecretRefField,
    String operatorConsoleUrlField,
    List<String> requiredRuntimeCapabilityIds,
    List<String> requiredMigrationIds,
    List<String> verificationPackIds,
    JsonNode configuration
) {
}
