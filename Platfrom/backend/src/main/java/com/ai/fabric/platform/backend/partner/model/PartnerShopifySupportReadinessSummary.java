package com.ai.fabric.platform.backend.partner.model;

import java.util.List;

public record PartnerShopifySupportReadinessSummary(
    String status,
    String message,
    String lifecycleStage,
    boolean orderLookupSupported,
    boolean orderLookupScopeGranted,
    boolean allOrdersScopeGranted,
    boolean appScopesUpdateWebhookReady,
    boolean installRecoveryRequired,
    String installRecoveryUrl,
    boolean scopeGrantRequired,
    String scopeGrantUrl,
    String installStatus,
    String billingTier,
    String billingStatus,
    List<String> grantedScopes,
    List<String> missingScopes,
    boolean merchantHandoffConfigured,
    String merchantHandoffMessage,
    List<String> nextActions,
    List<String> verificationMethods,
    List<String> supportedCapabilities,
    List<String> blockedCapabilities
) {
}
