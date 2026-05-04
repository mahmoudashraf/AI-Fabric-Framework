package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.action.service.ShopifyStorefrontMcpActionAdapter;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeStoreBillingState;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyScopeSupport;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeResolvedStoreCredentials;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordBillingStateRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordedBillingStateSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportProfileSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportSubscriptionSummary;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookSubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ShopifyBridgeSupportReadinessService {

    private static final String APP_SCOPES_UPDATE_TOPIC = "APP_SCOPES_UPDATE";

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyInstallRecordService installRecordService;
    private final ShopifyBridgeBillingService billingService;
    private final ShopifyWebhookSubscriptionService webhookSubscriptionService;
    private final ShopifyBridgeProperties properties;
    private final ShopifyStorefrontMcpActionAdapter mcpActionAdapter;

    @Autowired
    public ShopifyBridgeSupportReadinessService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                                ShopifyInstallRecordService installRecordService,
                                                ShopifyBridgeBillingService billingService,
                                                ShopifyWebhookSubscriptionService webhookSubscriptionService,
                                                ShopifyBridgeProperties properties,
                                                ShopifyStorefrontMcpActionAdapter mcpActionAdapter) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installRecordService = installRecordService;
        this.billingService = billingService;
        this.webhookSubscriptionService = webhookSubscriptionService;
        this.properties = properties;
        this.mcpActionAdapter = mcpActionAdapter;
    }

    ShopifyBridgeSupportReadinessService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                         ShopifyInstallRecordService installRecordService,
                                         ShopifyBridgeBillingService billingService,
                                         ShopifyWebhookSubscriptionService webhookSubscriptionService,
                                         ShopifyBridgeProperties properties) {
        this(
            platformShopifyStoreClient,
            installRecordService,
            billingService,
            webhookSubscriptionService,
            properties,
            null
        );
    }

    public ShopifyBridgeSupportReadinessSummary summarizeForShop(String shopDomain) {
        ShopifyInstallRecordSummary installRecord = installRecordService.findByShopDomain(shopDomain).orElse(null);
        SupportState supportState = resolveSupportState(shopDomain, installRecord);
        SupportBillingState billingState = resolveSupportBillingState(shopDomain, supportState);
        ShopifyBridgeSupportProfileSummary supportProfile = getSupportProfile(shopDomain);
        boolean installRecoveryRequired = installRecoveryRequired(installRecord);
        boolean orderLookupScopeGranted = ShopifyScopeSupport.hasScope(supportState.grantedScopes(), "read_orders");
        boolean allOrdersScopeGranted = ShopifyScopeSupport.hasScope(supportState.grantedScopes(), "read_all_orders");
        boolean scopesWebhookReady = supportState.appScopesUpdateWebhookReady();
        boolean orderLookupTierAllowed = "ELITE".equalsIgnoreCase(billingState.tierKey());
        List<String> missingScopes = orderLookupTierAllowed && !orderLookupScopeGranted ? List.of("read_orders") : List.of();
        boolean scopeGrantRequired = orderLookupTierAllowed && !installRecoveryRequired && !orderLookupScopeGranted;
        String scopeGrantUrl = scopeGrantRequired ? buildInstallUrl(shopDomain) : null;
        boolean orderLookupSupported = orderLookupTierAllowed && !installRecoveryRequired && orderLookupScopeGranted;
        boolean merchantHandoffConfigured = supportProfile.merchantHandoffConfigured();
        String status;
        String message;
        if (installRecoveryRequired) {
            status = "INSTALL_RECOVERY_REQUIRED";
            message = "This shop must complete the Shopify install flow again before storefront setup and support handoff can be trusted.";
        } else if ("PAYMENT_ISSUE".equalsIgnoreCase(billingState.status())) {
            status = "DEGRADED";
            message = "Shopify billing needs merchant review before the paid Companion support posture can be trusted for this store.";
        } else if ("CHECK_FAILED".equalsIgnoreCase(billingState.status())) {
            status = "DEGRADED";
            message = "Shopify billing posture could not be verified for this store. Review lifecycle and subscription state before go-live.";
        } else if (!orderLookupTierAllowed) {
            status = "READY";
            message = "Order lookup is outside the current Companion tier. Keep order-specific support on merchant handoff unless Elite order lookup is entitled and verified.";
        } else if (!orderLookupScopeGranted) {
            status = "PENDING_SCOPE_GRANT";
            message = "Customer-safe order lookup is waiting for Shopify order-read scope approval on this store.";
        } else if (!scopesWebhookReady) {
            status = "DEGRADED";
            message = "Order lookup can run, but the app-scopes webhook is not ready. Scope drift may require manual checks.";
        } else if (!allOrdersScopeGranted) {
            status = "READY";
            message = "Customer-safe order lookup is ready for recent orders. Orders older than Shopify's default window still require broader order access.";
        } else {
            status = "READY";
            message = "Customer-safe order lookup is ready for this store.";
        }
        String lifecycleStage = lifecycleStage(
            installRecoveryRequired,
            billingState.status(),
            orderLookupTierAllowed,
            orderLookupScopeGranted,
            scopesWebhookReady,
            merchantHandoffConfigured,
            allOrdersScopeGranted
        );
        List<String> nextActions = buildNextActions(
            installRecoveryRequired,
            orderLookupTierAllowed,
            orderLookupScopeGranted,
            allOrdersScopeGranted,
            scopesWebhookReady,
            merchantHandoffConfigured,
            supportProfile,
            billingState.status()
        );
        McpReadinessPosture mcpReadiness = resolveMcpReadiness(shopDomain);
        if (mcpReadiness.checked() && !mcpReadiness.ready()) {
            if ("READY".equalsIgnoreCase(status)) {
                status = "DEGRADED";
                message = "Shopify MCP endpoint/tool readiness is not verified for this store. Repair MCP drift before go-live.";
                lifecycleStage = "MCP_DRIFT";
            } else {
                message = message + " Shopify MCP endpoint/tool readiness is also not verified.";
            }
            nextActions = appendAll(nextActions, mcpReadiness.nextActions());
        }

        List<String> verificationMethods = orderLookupTierAllowed
            ? (merchantHandoffConfigured
                ? new ArrayList<>(List.of("ORDER_NUMBER_AND_EMAIL", "MERCHANT_SUPPORT_HANDOFF"))
                : new ArrayList<>(List.of("ORDER_NUMBER_AND_EMAIL")))
            : new ArrayList<>(List.of("MERCHANT_SUPPORT_HANDOFF"));
        if (mcpReadiness.checked()) {
            verificationMethods.add("SHOPIFY_MCP_TOOLS_LIST");
        }
        List<String> supportedCapabilities = orderLookupTierAllowed
            ? new ArrayList<>(List.of(
                "order-status",
                "fulfillment-status",
                "tracking-link",
                "line-items",
                "billing-status"
            ))
            : new ArrayList<>(List.of("merchant-handoff"));
        supportedCapabilities.addAll(mcpReadiness.supportedCapabilities());
        List<String> blockedCapabilities = new ArrayList<>(List.of(
            "refunds",
            "order-edits",
            "address-changes",
            "payment-details",
            "customer-profile"
        ));
        blockedCapabilities.addAll(mcpReadiness.blockedCapabilities());

        return new ShopifyBridgeSupportReadinessSummary(
            normalizeShopDomain(shopDomain),
            status,
            message,
            lifecycleStage,
            orderLookupSupported,
            orderLookupScopeGranted,
            allOrdersScopeGranted,
            scopesWebhookReady,
            installRecoveryRequired,
            installRecoveryRequired ? buildInstallUrl(shopDomain) : null,
            scopeGrantRequired,
            scopeGrantUrl,
            supportState.installStatus(),
            billingState.tierKey(),
            billingState.status(),
            supportState.grantedScopes(),
            missingScopes,
            supportState.activeSubscriptionNames(),
            supportState.activeSubscriptions(),
            supportProfile,
            merchantHandoffConfigured,
            merchantHandoffMessage(supportProfile),
            nextActions,
            List.copyOf(verificationMethods),
            List.copyOf(supportedCapabilities),
            List.copyOf(blockedCapabilities)
        );
    }

    private SupportState resolveSupportState(String shopDomain,
                                             ShopifyInstallRecordSummary installRecord) {
        if (installRecord != null) {
            return reconcilePersistedSupportState(shopDomain, installRecord);
        }
        try {
            ShopifyBridgeResolvedStoreCredentials resolved = platformShopifyStoreClient.resolveCredentialMaterial(shopDomain);
            if (resolved == null || resolved.accessToken() == null || resolved.accessToken().isBlank()) {
                return new SupportState(List.of(), List.of(), false, "UNKNOWN", null, null);
            }
            installRecordService.recordInstall(
                shopDomain,
                "https://" + normalizeShopDomain(shopDomain),
                null,
                resolved.scopesText(),
                null,
                null,
                resolved.accessTokenExpiresAt(),
                resolved.refreshTokenExpiresAt()
            );
            installRecordService.recordAppScopesUpdateWebhookReady(
                shopDomain,
                inspectAppScopesWebhookReady(shopDomain, resolved.accessToken())
            );
            recordBillingStateSafely(shopDomain, resolved.accessToken());
            return installRecordService.findByShopDomain(shopDomain)
                .map(this::toSupportState)
                .orElseGet(() -> new SupportState(
                    ShopifyScopeSupport.parseScopes(resolved.scopesText()),
                    List.of(),
                    false,
                    "INSTALLED",
                    null,
                    null
                ));
        } catch (Exception ex) {
            return new SupportState(
                List.of(),
                List.of(),
                false,
                "UNKNOWN",
                null,
                null
            );
        }
    }

    private SupportState reconcilePersistedSupportState(String shopDomain,
                                                        ShopifyInstallRecordSummary installRecord) {
        SupportState current = toSupportState(installRecord);
        if ("UNINSTALLED".equalsIgnoreCase(current.installStatus())
            || !requiresLiveSupportRefresh(installRecord, current)) {
            return current;
        }
        try {
            ShopifyBridgeResolvedStoreCredentials resolved = platformShopifyStoreClient.resolveCredentialMaterial(shopDomain);
            if (resolved == null || optionalText(resolved.accessToken()) == null) {
                return current;
            }
            List<String> grantedScopes = current.grantedScopes();
            String resolvedScopesText = optionalText(resolved.scopesText());
            if (resolvedScopesText != null) {
                grantedScopes = ShopifyScopeSupport.parseScopes(resolvedScopesText);
                persistResolvedScopesIfChanged(installRecord, resolved, resolvedScopesText);
            }
            boolean webhookReady = current.appScopesUpdateWebhookReady();
            if (!webhookReady) {
                webhookReady = inspectAppScopesWebhookReady(shopDomain, resolved.accessToken());
                installRecordService.recordAppScopesUpdateWebhookReady(shopDomain, webhookReady);
            }

            String billingTierKey = current.billingTierKey();
            String billingStatus = current.billingStatus();
            List<ShopifyBridgeSupportSubscriptionSummary> activeSubscriptions = current.activeSubscriptions();
            if (billingTierKey == null || billingStatus == null) {
                ShopifyBridgeStoreBillingState billingState = inspectBillingStateSafely(shopDomain, resolved.accessToken());
                if (billingState != null) {
                    billingTierKey = billingState.tierKey();
                    billingStatus = billingState.status();
                    activeSubscriptions = billingState.activeSubscriptions() == null
                        ? List.of()
                        : billingState.activeSubscriptions();
                }
            }
            return new SupportState(
                grantedScopes,
                activeSubscriptions,
                webhookReady,
                current.installStatus(),
                billingTierKey,
                billingStatus
            );
        } catch (Exception ex) {
            return current;
        }
    }

    private SupportBillingState resolveSupportBillingState(String shopDomain, SupportState supportState) {
        SupportBillingState platformBillingState = resolvePlatformBillingState(shopDomain);
        if (platformBillingState != null) {
            return platformBillingState;
        }
        if (optionalText(supportState.billingTierKey()) != null || optionalText(supportState.billingStatus()) != null) {
            return new SupportBillingState(
                optionalText(supportState.billingTierKey()) == null ? "FREE" : supportState.billingTierKey(),
                optionalText(supportState.billingStatus()) == null ? "ACTIVE" : supportState.billingStatus()
            );
        }
        ShopifyBridgeBillingSummary baseline = billingService.summarize();
        String status = baseline == null || baseline.status() == null || baseline.status().isBlank()
            ? "ACTIVE"
            : baseline.status();
        String tierKey = baseline == null || baseline.tierKey() == null || baseline.tierKey().isBlank()
            ? "FREE"
            : baseline.tierKey();

        for (ShopifyBridgeSupportSubscriptionSummary subscription : supportState.activeSubscriptions()) {
            String subscriptionStatus = optionalText(subscription.status());
            if ("FROZEN".equalsIgnoreCase(subscriptionStatus)) {
                return new SupportBillingState(tierKey, "PAYMENT_ISSUE");
            }
            if (!subscription.active()) {
                continue;
            }
            if ("ELITE".equalsIgnoreCase(subscription.tierKey())) {
                return new SupportBillingState("ELITE", "ACTIVE");
            }
            if ("STARTER".equalsIgnoreCase(subscription.tierKey())) {
                return new SupportBillingState("STARTER", "ACTIVE");
            }
        }
        return new SupportBillingState(tierKey, status);
    }

    private SupportBillingState resolvePlatformBillingState(String shopDomain) {
        try {
            ShopifyBridgeRecordedBillingStateSummary platformBillingState =
                platformShopifyStoreClient.getBillingState(shopDomain);
            if (platformBillingState == null
                || (optionalText(platformBillingState.tierKey()) == null
                    && optionalText(platformBillingState.status()) == null)) {
                return null;
            }
            return new SupportBillingState(
                optionalText(platformBillingState.tierKey()) == null ? "FREE" : platformBillingState.tierKey(),
                optionalText(platformBillingState.status()) == null ? "ACTIVE" : platformBillingState.status()
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private SupportState toSupportState(ShopifyInstallRecordSummary installRecord) {
        return new SupportState(
            fallbackScopes(installRecord),
            installRecord.activeSubscriptions() == null ? List.of() : installRecord.activeSubscriptions(),
            installRecord.appScopesUpdateWebhookReady(),
            installRecord.status(),
            installRecord.billingTierKey(),
            installRecord.billingStatus()
        );
    }

    private List<String> fallbackScopes(ShopifyInstallRecordSummary installRecord) {
        return ShopifyScopeSupport.parseScopes(installRecord == null ? null : installRecord.scopesText());
    }

    private boolean requiresLiveSupportRefresh(ShopifyInstallRecordSummary installRecord,
                                               SupportState current) {
        return !current.appScopesUpdateWebhookReady()
            || optionalText(current.billingTierKey()) == null
            || optionalText(current.billingStatus()) == null
            || (installRecord.appScopesUpdateWebhookCheckedAt() == null
                && "INSTALLED".equalsIgnoreCase(current.installStatus()));
    }

    private void persistResolvedScopesIfChanged(ShopifyInstallRecordSummary installRecord,
                                                ShopifyBridgeResolvedStoreCredentials resolved,
                                                String resolvedScopesText) {
        String persistedScopesText = optionalText(installRecord.scopesText());
        if (resolvedScopesText.equals(persistedScopesText)) {
            return;
        }
        installRecordService.recordCredentials(
            installRecord.shopDomain(),
            installRecord.accessTokenSecretRef(),
            installRecord.refreshTokenSecretRef(),
            resolved.accessTokenExpiresAt(),
            resolved.refreshTokenExpiresAt(),
            resolvedScopesText
        );
    }

    private boolean installRecoveryRequired(ShopifyInstallRecordSummary installRecord) {
        return "UNINSTALLED".equalsIgnoreCase(installRecord == null ? null : installRecord.status());
    }

    private ShopifyBridgeSupportProfileSummary getSupportProfile(String shopDomain) {
        ShopifyBridgeSupportProfileSummary summary;
        try {
            summary = platformShopifyStoreClient.getSupportProfile(shopDomain);
            summary = summary == null
                ? new ShopifyBridgeSupportProfileSummary(null, null, null, null, null, false)
                : summary;
        } catch (HttpClientErrorException.NotFound ex) {
            summary = new ShopifyBridgeSupportProfileSummary(null, null, null, null, null, false);
        } catch (Exception ex) {
            summary = new ShopifyBridgeSupportProfileSummary(null, null, null, null, null, false);
        }
        return summary;
    }

    private String buildInstallUrl(String shopDomain) {
        String baseUrl = properties.publicBaseUrl();
        String normalizedBaseUrl = (baseUrl == null || baseUrl.isBlank())
            ? ""
            : (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        return UriComponentsBuilder.fromUriString(normalizedBaseUrl + "/auth/shopify/install")
            .queryParam("shop", normalizeShopDomain(shopDomain))
            .build(true)
            .toUriString();
    }

    private String optionalText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String normalizeShopDomain(String shopDomain) {
        return shopDomain == null ? "" : shopDomain.trim().toLowerCase(Locale.ROOT);
    }

    private String lifecycleStage(boolean installRecoveryRequired,
                                  String billingStatus,
                                  boolean orderLookupTierAllowed,
                                  boolean orderLookupScopeGranted,
                                  boolean scopesWebhookReady,
                                  boolean merchantHandoffConfigured,
                                  boolean allOrdersScopeGranted) {
        if (installRecoveryRequired) {
            return "INSTALL_RECOVERY";
        }
        if ("PAYMENT_ISSUE".equalsIgnoreCase(billingStatus)) {
            return "BILLING_REVIEW";
        }
        if (!orderLookupTierAllowed) {
            return merchantHandoffConfigured ? "MERCHANT_HANDOFF" : "SUPPORT_HANDOFF_SETUP";
        }
        if (!orderLookupScopeGranted) {
            return "SCOPE_APPROVAL";
        }
        if (!scopesWebhookReady) {
            return "WEBHOOK_REPAIR";
        }
        if (!merchantHandoffConfigured) {
            return "SUPPORT_HANDOFF_SETUP";
        }
        if (!allOrdersScopeGranted) {
            return "RECENT_ORDER_ONLY";
        }
        return "READY";
    }

    private List<String> buildNextActions(boolean installRecoveryRequired,
                                          boolean orderLookupTierAllowed,
                                          boolean orderLookupScopeGranted,
                                          boolean allOrdersScopeGranted,
                                          boolean scopesWebhookReady,
                                          boolean merchantHandoffConfigured,
                                          ShopifyBridgeSupportProfileSummary supportProfile,
                                          String billingStatus) {
        List<String> actions = new ArrayList<>();
        if (installRecoveryRequired) {
            actions.add("Complete the Shopify install flow again for this store before relying on storefront setup or support handoff.");
        }
        if (orderLookupTierAllowed && !orderLookupScopeGranted) {
            actions.add("Grant Shopify read_orders scope so customer-safe order lookup can verify recent orders.");
        }
        if (orderLookupTierAllowed && !allOrdersScopeGranted) {
            actions.add("Decide whether historical order support needs read_all_orders before launch commitments are made.");
        }
        if (orderLookupTierAllowed && !scopesWebhookReady) {
            actions.add("Repair the APP_SCOPES_UPDATE webhook so order-scope drift is detected automatically.");
        }
        if (!merchantHandoffConfigured) {
            actions.add("Configure a merchant support email, contact URL, or help center URL for unsupported order and account cases.");
        }
        if (orderLookupTierAllowed && orderLookupScopeGranted && optionalText(supportProfile.orderLookupPageUrl()) == null) {
            actions.add("Publish the order lookup block on a support or contact page and save that page URL in the merchant support profile.");
        }
        if ("PAYMENT_ISSUE".equalsIgnoreCase(billingStatus)) {
            actions.add("Resolve the current Shopify billing issue before go-live or paid-surface expansion.");
        }
        if ("CHECK_FAILED".equalsIgnoreCase(billingStatus)) {
            actions.add("Re-run Shopify billing verification so lifecycle and subscription posture is grounded before go-live.");
        }
        if (actions.isEmpty()) {
            actions.add("No blocking support lifecycle actions remain for this store.");
        }
        return List.copyOf(actions);
    }

    private McpReadinessPosture resolveMcpReadiness(String shopDomain) {
        if (mcpActionAdapter == null) {
            return McpReadinessPosture.unchecked();
        }
        try {
            Map<String, Object> readiness = mcpActionAdapter.readiness(normalizeShopDomain(shopDomain));
            boolean ready = readiness != null && Boolean.TRUE.equals(readiness.get("ready"));
            Object serversValue = readiness == null ? null : readiness.get("servers");
            if (!(serversValue instanceof List<?> servers)) {
                return ready
                    ? McpReadinessPosture.ready(List.of())
                    : McpReadinessPosture.blocked(
                        List.of("mcp:shopify:tools-list"),
                        List.of("Repair Shopify MCP endpoint/tool readiness before go-live.")
                    );
            }

            List<String> supportedCapabilities = new ArrayList<>();
            List<String> blockedCapabilities = new ArrayList<>();
            List<String> nextActions = new ArrayList<>();
            for (Object serverValue : servers) {
                if (!(serverValue instanceof Map<?, ?> server)) {
                    continue;
                }
                String serverRef = optionalText(server.get("serverRef"));
                boolean serverReady = Boolean.TRUE.equals(server.get("ready"));
                List<String> expectedTools = stringList(server.get("expectedTools"));
                List<String> missingTools = stringList(server.get("missingTools"));
                List<String> presentTools = stringList(server.get("presentTools"));
                List<String> supportedTools = presentTools.isEmpty()
                    ? expectedTools
                    : presentTools.stream()
                        .filter(present -> expectedTools.stream().anyMatch(expected -> expected.equalsIgnoreCase(present)))
                        .toList();
                for (String tool : supportedTools) {
                    supportedCapabilities.add("mcp:" + safeCapabilitySegment(serverRef) + ":" + tool);
                }
                if (!serverReady) {
                    if (missingTools.isEmpty()) {
                        blockedCapabilities.add("mcp:" + safeCapabilitySegment(serverRef) + ":tools-list");
                        nextActions.add("Repair Shopify MCP tools/list readiness for " + safeServerLabel(serverRef) + ".");
                    } else {
                        blockedCapabilities.addAll(missingTools.stream()
                            .map(tool -> "mcp:" + safeCapabilitySegment(serverRef) + ":" + tool)
                            .toList());
                        nextActions.add("Repair Shopify MCP drift for " + safeServerLabel(serverRef)
                            + "; missing tools: " + String.join(", ", missingTools) + ".");
                    }
                }
            }
            if (ready) {
                return McpReadinessPosture.ready(dedupe(supportedCapabilities));
            }
            if (nextActions.isEmpty()) {
                nextActions.add("Repair Shopify MCP endpoint/tool readiness before go-live.");
            }
            return McpReadinessPosture.blocked(
                dedupe(blockedCapabilities),
                dedupe(nextActions),
                dedupe(supportedCapabilities)
            );
        } catch (RuntimeException ex) {
            return McpReadinessPosture.blocked(
                List.of("mcp:shopify:tools-list"),
                List.of("Repair Shopify MCP endpoint/tool readiness before go-live.")
            );
        }
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .map(this::optionalText)
            .filter(text -> text != null)
            .collect(Collectors.toList());
    }

    private String safeServerLabel(String serverRef) {
        return optionalText(serverRef) == null ? "Shopify MCP" : serverRef;
    }

    private String safeCapabilitySegment(String value) {
        String normalized = optionalText(value);
        return normalized == null ? "shopify" : normalized;
    }

    private List<String> appendAll(List<String> base, List<String> additions) {
        List<String> merged = new ArrayList<>(base == null ? List.of() : base);
        if (additions != null) {
            merged.addAll(additions);
        }
        return List.copyOf(dedupe(merged));
    }

    private List<String> dedupe(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
            .filter(value -> value != null && !value.isBlank())
            .distinct()
            .toList();
    }

    private boolean inspectAppScopesWebhookReady(String shopDomain, String accessToken) {
        try {
            return "READY".equalsIgnoreCase(
                webhookSubscriptionService.inspectTopicStatus(shopDomain, accessToken, APP_SCOPES_UPDATE_TOPIC).status()
            );
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void recordBillingStateSafely(String shopDomain, String accessToken) {
        inspectBillingStateSafely(shopDomain, accessToken);
    }

    private ShopifyBridgeStoreBillingState inspectBillingStateSafely(String shopDomain, String accessToken) {
        try {
            ShopifyBridgeStoreBillingState billingState = billingService.inspectStoreBillingState(shopDomain, accessToken);
            platformShopifyStoreClient.recordBillingState(
                shopDomain,
                new ShopifyBridgeRecordBillingStateRequest(
                    billingState.tierKey(),
                    billingState.status(),
                    null,
                    null,
                    "Support readiness verification refreshed billing state."
                )
            );
            installRecordService.recordBillingState(
                shopDomain,
                billingState.tierKey(),
                billingState.status(),
                billingState.activeSubscriptions()
            );
            return billingState;
        } catch (RuntimeException ignored) {
            // Preserve the lightweight fallback path when Shopify billing is temporarily unavailable.
            return null;
        }
    }

    private String merchantHandoffMessage(ShopifyBridgeSupportProfileSummary supportProfile) {
        if (!supportProfile.merchantHandoffConfigured()) {
            return "Merchant support handoff is not configured yet. Add a support email, contact URL, or help center URL before launch.";
        }
        List<String> channels = new ArrayList<>();
        if (optionalText(supportProfile.contactEmail()) != null) {
            channels.add("support email");
        }
        if (optionalText(supportProfile.contactUrl()) != null) {
            channels.add("contact page");
        }
        if (optionalText(supportProfile.helpCenterUrl()) != null) {
            channels.add("help center");
        }
        if (optionalText(supportProfile.orderLookupPageUrl()) != null) {
            channels.add("order lookup page");
        }
        return channels.isEmpty()
            ? "Merchant support handoff is configured."
            : "Merchant support handoff is configured through " + String.join(", ", channels) + ".";
    }

    private record SupportState(
        List<String> grantedScopes,
        List<ShopifyBridgeSupportSubscriptionSummary> activeSubscriptions,
        boolean appScopesUpdateWebhookReady,
        String installStatus,
        String billingTierKey,
        String billingStatus
    ) {
        private List<String> activeSubscriptionNames() {
            return activeSubscriptions.stream()
                .filter(ShopifyBridgeSupportSubscriptionSummary::active)
                .map(ShopifyBridgeSupportSubscriptionSummary::name)
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .toList();
        }
    }

    private record SupportBillingState(
        String tierKey,
        String status
    ) {
    }

    private record McpReadinessPosture(
        boolean checked,
        boolean ready,
        List<String> supportedCapabilities,
        List<String> blockedCapabilities,
        List<String> nextActions
    ) {
        private static McpReadinessPosture unchecked() {
            return new McpReadinessPosture(false, true, List.of(), List.of(), List.of());
        }

        private static McpReadinessPosture ready(List<String> supportedCapabilities) {
            return new McpReadinessPosture(true, true, supportedCapabilities, List.of(), List.of());
        }

        private static McpReadinessPosture blocked(List<String> blockedCapabilities,
                                                   List<String> nextActions) {
            return blocked(blockedCapabilities, nextActions, List.of());
        }

        private static McpReadinessPosture blocked(List<String> blockedCapabilities,
                                                   List<String> nextActions,
                                                   List<String> supportedCapabilities) {
            return new McpReadinessPosture(true, false, supportedCapabilities, blockedCapabilities, nextActions);
        }
    }
}
