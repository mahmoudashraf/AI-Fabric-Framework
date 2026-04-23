package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyScopeSupport;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportProfileSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportSubscriptionSummary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ShopifyBridgeSupportReadinessService {

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyInstallRecordService installRecordService;
    private final ShopifyBridgeBillingService billingService;
    private final ShopifyBridgeProperties properties;

    public ShopifyBridgeSupportReadinessService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                                ShopifyInstallRecordService installRecordService,
                                                ShopifyBridgeBillingService billingService,
                                                ShopifyBridgeProperties properties) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installRecordService = installRecordService;
        this.billingService = billingService;
        this.properties = properties;
    }

    public ShopifyBridgeSupportReadinessSummary summarizeForShop(String shopDomain) {
        ShopifyInstallRecordSummary installRecord = installRecordService.findByShopDomain(shopDomain).orElse(null);
        SupportState supportState = resolveSupportState(installRecord);
        SupportBillingState billingState = resolveSupportBillingState(supportState);
        ShopifyBridgeSupportProfileSummary supportProfile = getSupportProfile(shopDomain);
        boolean installRecoveryRequired = installRecoveryRequired(installRecord);
        boolean orderLookupScopeGranted = ShopifyScopeSupport.hasScope(supportState.grantedScopes(), "read_orders");
        boolean allOrdersScopeGranted = ShopifyScopeSupport.hasScope(supportState.grantedScopes(), "read_all_orders");
        boolean scopesWebhookReady = supportState.appScopesUpdateWebhookReady();
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

    private SupportState resolveSupportState(ShopifyInstallRecordSummary installRecord) {
        return new SupportState(
            fallbackScopes(installRecord),
            List.of(),
            installRecord != null && installRecord.appScopesUpdateWebhookReady()
        );
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

    private record SupportState(
        List<String> grantedScopes,
        List<ShopifyBridgeSupportSubscriptionSummary> activeSubscriptions,
        boolean appScopesUpdateWebhookReady
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
