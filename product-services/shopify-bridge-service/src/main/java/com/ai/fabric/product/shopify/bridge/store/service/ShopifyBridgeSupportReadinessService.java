package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyScopeSupport;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportProfileSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportSubscriptionSummary;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookSubscriptionDiagnosticsService;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ShopifyBridgeSupportReadinessService {

    private static final String SUPPORT_STATE_QUERY = """
        query ShopifyBridgeSupportState {
          currentAppInstallation {
            accessScopes {
              handle
            }
            activeSubscriptions {
              id
              name
              status
            }
          }
        }
        """;

    private static final String SHOP_CONTACT_QUERY = """
        query ShopifyBridgeShopContact {
          shop {
            contactEmail
            email
          }
        }
        """;

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyInstallRecordService installRecordService;
    private final ShopifyBridgeBillingService billingService;
    private final ShopifyWebhookSubscriptionDiagnosticsService webhookSubscriptionDiagnosticsService;
    private final ShopifyAdminGraphqlClient shopifyAdminGraphqlClient;
    private final ShopifyBridgeProperties properties;

    public ShopifyBridgeSupportReadinessService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                                ShopifyBridgeInstallCredentialService installCredentialService,
                                                ShopifyInstallRecordService installRecordService,
                                                ShopifyBridgeBillingService billingService,
                                                ShopifyWebhookSubscriptionDiagnosticsService webhookSubscriptionDiagnosticsService,
                                                ShopifyAdminGraphqlClient shopifyAdminGraphqlClient,
                                                ShopifyBridgeProperties properties) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installCredentialService = installCredentialService;
        this.installRecordService = installRecordService;
        this.billingService = billingService;
        this.webhookSubscriptionDiagnosticsService = webhookSubscriptionDiagnosticsService;
        this.shopifyAdminGraphqlClient = shopifyAdminGraphqlClient;
        this.properties = properties;
    }

    public ShopifyBridgeSupportReadinessSummary summarizeForShop(String shopDomain) {
        ShopifyInstallRecordSummary installRecord = installRecordService.findByShopDomain(shopDomain).orElse(null);
        ShopifyBridgeCredentialAcquisition acquisition = installCredentialService.resolvePersistedMaterial(shopDomain).orElse(null);
        String accessToken = acquisition == null ? null : acquisition.tokenExchangeMaterial().accessToken();
        SupportState supportState = resolveSupportState(shopDomain, accessToken, installRecord);
        SupportBillingState billingState = resolveSupportBillingState(supportState);
        ShopifyBridgeSupportProfileSummary supportProfile = getSupportProfile(shopDomain, accessToken);
        boolean installRecoveryRequired = installRecoveryRequired(installRecord);
        boolean orderLookupScopeGranted = ShopifyScopeSupport.hasScope(supportState.grantedScopes(), "read_orders");
        boolean allOrdersScopeGranted = ShopifyScopeSupport.hasScope(supportState.grantedScopes(), "read_all_orders");
        boolean scopesWebhookReady = "READY".equalsIgnoreCase(
            webhookSubscriptionDiagnosticsService.topicForShop(shopDomain, "APP_SCOPES_UPDATE").status()
        );
        List<String> missingScopes = orderLookupScopeGranted ? List.of() : List.of("read_orders");
        boolean scopeGrantRequired = !installRecoveryRequired && !orderLookupScopeGranted;
        String scopeGrantUrl = scopeGrantRequired ? buildInstallUrl(shopDomain) : null;
        boolean orderLookupSupported = !installRecoveryRequired && orderLookupScopeGranted;
        boolean merchantHandoffConfigured = supportProfile.merchantHandoffConfigured();
        String status;
        String message;
        if (installRecoveryRequired) {
            status = "INSTALL_RECOVERY_REQUIRED";
            message = "This shop must complete the Shopify install flow again before customer-safe order lookup can run.";
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
            orderLookupScopeGranted,
            scopesWebhookReady,
            merchantHandoffConfigured,
            allOrdersScopeGranted
        );
        List<String> nextActions = buildNextActions(
            installRecoveryRequired,
            orderLookupScopeGranted,
            allOrdersScopeGranted,
            scopesWebhookReady,
            merchantHandoffConfigured,
            supportProfile,
            billingState.status()
        );

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
            installRecord == null ? "UNKNOWN" : installRecord.status(),
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
            merchantHandoffConfigured
                ? List.of("ORDER_NUMBER_AND_EMAIL", "MERCHANT_SUPPORT_HANDOFF")
                : List.of("ORDER_NUMBER_AND_EMAIL"),
            List.of(
                "order-status",
                "fulfillment-status",
                "tracking-link",
                "line-items",
                "billing-status"
            ),
            List.of(
                "refunds",
                "order-edits",
                "address-changes",
                "payment-details",
                "customer-profile"
            )
        );
    }

    private SupportState resolveSupportState(String shopDomain,
                                             String accessToken,
                                             ShopifyInstallRecordSummary installRecord) {
        if (accessToken == null || accessToken.isBlank()) {
            return new SupportState(
                fallbackScopes(installRecord),
                List.of()
            );
        }
        try {
            Map<String, Object> response = shopifyAdminGraphqlClient.execute(shopDomain, accessToken, SUPPORT_STATE_QUERY);
            failOnGraphQlErrors(response, "Shopify support readiness lookup failed.");
            Map<String, Object> data = requireMap(response.get("data"), "Shopify support readiness lookup returned no data.");
            Map<String, Object> installation = requireMap(
                data.get("currentAppInstallation"),
                "Shopify support readiness lookup returned no currentAppInstallation payload."
            );
            List<String> grantedScopes = parseScopes(installation.get("accessScopes"));
            if (grantedScopes.isEmpty()) {
                grantedScopes = fallbackScopes(installRecord);
            }
            return new SupportState(
                grantedScopes,
                parseActiveSubscriptions(installation.get("activeSubscriptions"))
            );
        } catch (ResponseStatusException ex) {
            return new SupportState(
                fallbackScopes(installRecord),
                List.of()
            );
        }
    }

    private SupportBillingState resolveSupportBillingState(SupportState supportState) {
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

    private List<String> fallbackScopes(ShopifyInstallRecordSummary installRecord) {
        return ShopifyScopeSupport.parseScopes(installRecord == null ? null : installRecord.scopesText());
    }

    private List<String> parseScopes(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        Set<String> scopes = new LinkedHashSet<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object handle = map.get("handle");
            if (handle != null) {
                String normalized = handle.toString().trim().toLowerCase(Locale.ROOT);
                if (!normalized.isEmpty()) {
                    scopes.add(normalized);
                }
            }
        }
        return List.copyOf(scopes);
    }

    private List<ShopifyBridgeSupportSubscriptionSummary> parseActiveSubscriptions(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        List<ShopifyBridgeSupportSubscriptionSummary> subscriptions = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String status = optionalText(map.get("status"));
            String name = optionalText(map.get("name"));
            String subscriptionId = optionalText(map.get("id"));
            if (subscriptionId == null && name == null && status == null) {
                continue;
            }
            boolean active = "ACTIVE".equalsIgnoreCase(status) || "ACCEPTED".equalsIgnoreCase(status);
            subscriptions.add(new ShopifyBridgeSupportSubscriptionSummary(
                subscriptionId,
                name,
                status == null ? "UNKNOWN" : status.toUpperCase(Locale.ROOT),
                resolveTierKey(name),
                active
            ));
        }
        return List.copyOf(subscriptions);
    }

    private boolean installRecoveryRequired(ShopifyInstallRecordSummary installRecord) {
        return "UNINSTALLED".equalsIgnoreCase(installRecord == null ? null : installRecord.status());
    }

    private ShopifyBridgeSupportProfileSummary getSupportProfile(String shopDomain, String accessToken) {
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
        if (summary.merchantHandoffConfigured() || accessToken == null || accessToken.isBlank()) {
            return summary;
        }
        return mergeSupportProfile(summary, resolveShopContactProfile(shopDomain, accessToken));
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

    private void failOnGraphQlErrors(Map<String, Object> response, String fallbackMessage) {
        Object errors = response.get("errors");
        if (!(errors instanceof List<?> errorList) || errorList.isEmpty()) {
            return;
        }
        List<String> messages = new ArrayList<>();
        for (Object error : errorList) {
            if (!(error instanceof Map<?, ?> map)) {
                continue;
            }
            String message = optionalText(map.get("message"));
            if (message != null) {
                messages.add(message);
            }
        }
        throw new ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_GATEWAY,
            messages.isEmpty() ? fallbackMessage : String.join(" ", messages)
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMap(Object value, String message) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, message);
        }
        return (Map<String, Object>) map;
    }

    private String optionalText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private ShopifyBridgeSupportProfileSummary resolveShopContactProfile(String shopDomain, String accessToken) {
        try {
            Map<String, Object> response = shopifyAdminGraphqlClient.execute(shopDomain, accessToken, SHOP_CONTACT_QUERY);
            failOnGraphQlErrors(response, "Shopify shop contact lookup failed.");
            Map<String, Object> data = requireMap(response.get("data"), "Shopify shop contact lookup returned no data.");
            Map<String, Object> shop = requireMap(data.get("shop"), "Shopify shop contact lookup returned no shop payload.");
            String contactEmail = optionalText(shop.get("contactEmail"));
            if (contactEmail == null) {
                contactEmail = optionalText(shop.get("email"));
            }
            return new ShopifyBridgeSupportProfileSummary(
                contactEmail,
                null,
                null,
                null,
                null,
                contactEmail != null
            );
        } catch (ResponseStatusException ex) {
            return new ShopifyBridgeSupportProfileSummary(null, null, null, null, null, false);
        }
    }

    private ShopifyBridgeSupportProfileSummary mergeSupportProfile(ShopifyBridgeSupportProfileSummary persisted,
                                                                  ShopifyBridgeSupportProfileSummary fallback) {
        String contactEmail = optionalText(persisted.contactEmail());
        if (contactEmail == null) {
            contactEmail = optionalText(fallback.contactEmail());
        }
        String contactUrl = optionalText(persisted.contactUrl());
        String helpCenterUrl = optionalText(persisted.helpCenterUrl());
        String orderLookupPageUrl = optionalText(persisted.orderLookupPageUrl());
        String supportPolicyNote = optionalText(persisted.supportPolicyNote());
        boolean merchantHandoffConfigured = contactEmail != null
            || contactUrl != null
            || helpCenterUrl != null;
        return new ShopifyBridgeSupportProfileSummary(
            contactEmail,
            contactUrl,
            helpCenterUrl,
            orderLookupPageUrl,
            supportPolicyNote,
            merchantHandoffConfigured
        );
    }

    private String normalizeShopDomain(String shopDomain) {
        return shopDomain == null ? "" : shopDomain.trim().toLowerCase(Locale.ROOT);
    }

    private String lifecycleStage(boolean installRecoveryRequired,
                                  String billingStatus,
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
                                          boolean orderLookupScopeGranted,
                                          boolean allOrdersScopeGranted,
                                          boolean scopesWebhookReady,
                                          boolean merchantHandoffConfigured,
                                          ShopifyBridgeSupportProfileSummary supportProfile,
                                          String billingStatus) {
        List<String> actions = new ArrayList<>();
        if (installRecoveryRequired) {
            actions.add("Complete the Shopify install flow again for this store before relying on governed support features.");
        }
        if (!orderLookupScopeGranted) {
            actions.add("Grant Shopify read_orders scope so customer-safe order lookup can verify recent orders.");
        }
        if (!allOrdersScopeGranted) {
            actions.add("Decide whether historical order support needs read_all_orders before launch commitments are made.");
        }
        if (!scopesWebhookReady) {
            actions.add("Repair the APP_SCOPES_UPDATE webhook so order-scope drift is detected automatically.");
        }
        if (!merchantHandoffConfigured) {
            actions.add("Configure a merchant support email, contact URL, or help center URL for unsupported order and account cases.");
        }
        if (orderLookupScopeGranted && optionalText(supportProfile.orderLookupPageUrl()) == null) {
            actions.add("Publish the order lookup block on a support or contact page and save that page URL in the merchant support profile.");
        }
        if ("PAYMENT_ISSUE".equalsIgnoreCase(billingStatus)) {
            actions.add("Resolve the current Shopify billing issue before go-live or paid-surface expansion.");
        }
        if (actions.isEmpty()) {
            actions.add("No blocking support lifecycle actions remain for this store.");
        }
        return List.copyOf(actions);
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

    private String resolveTierKey(String subscriptionName) {
        String normalized = optionalText(subscriptionName);
        if (normalized == null) {
            return "UNKNOWN";
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.contains("elite")) {
            return "ELITE";
        }
        if (lower.contains("starter")) {
            return "STARTER";
        }
        if (lower.contains("free")) {
            return "FREE";
        }
        return "UNKNOWN";
    }

    private record SupportState(
        List<String> grantedScopes,
        List<ShopifyBridgeSupportSubscriptionSummary> activeSubscriptions
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
}
