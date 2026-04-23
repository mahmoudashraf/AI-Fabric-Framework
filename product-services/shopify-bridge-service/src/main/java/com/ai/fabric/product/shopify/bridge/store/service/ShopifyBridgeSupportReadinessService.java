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
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportReadinessSummary;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionTopicStatusSummary;
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
        ShopifyBridgeStoreSummary store = getStoreOrNull(shopDomain);
        ShopifyInstallRecordSummary installRecord = installRecordService.findByShopDomain(shopDomain).orElse(null);
        ShopifyBridgeCredentialAcquisition acquisition = installCredentialService.resolvePersistedMaterial(shopDomain).orElse(null);
        String accessToken = acquisition == null ? null : acquisition.tokenExchangeMaterial().accessToken();
        ShopifyBridgeBillingSummary billingSummary = billingService.summarizeForShop(shopDomain, accessToken);
        ShopifyWebhookSubscriptionStatusSummary webhookSummary = store == null
            ? null
            : webhookSubscriptionDiagnosticsService.forShop(shopDomain);
        SupportState supportState = resolveSupportState(shopDomain, accessToken, installRecord);
        boolean installRecoveryRequired = installRecoveryRequired(installRecord, store);
        boolean orderLookupScopeGranted = ShopifyScopeSupport.hasScope(supportState.grantedScopes(), "read_orders");
        boolean allOrdersScopeGranted = ShopifyScopeSupport.hasScope(supportState.grantedScopes(), "read_all_orders");
        boolean scopesWebhookReady = hasReadyWebhookTopic(webhookSummary, "APP_SCOPES_UPDATE");
        List<String> missingScopes = orderLookupScopeGranted ? List.of() : List.of("read_orders");
        boolean orderLookupSupported = !installRecoveryRequired && orderLookupScopeGranted;
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

        return new ShopifyBridgeSupportReadinessSummary(
            normalizeShopDomain(shopDomain),
            status,
            message,
            orderLookupSupported,
            orderLookupScopeGranted,
            allOrdersScopeGranted,
            scopesWebhookReady,
            installRecoveryRequired,
            installRecoveryRequired ? buildInstallUrl(shopDomain) : null,
            store == null ? (installRecord == null ? "UNKNOWN" : installRecord.status()) : store.installStatus(),
            billingSummary.tierKey(),
            billingSummary.status(),
            supportState.grantedScopes(),
            missingScopes,
            supportState.activeSubscriptionNames(),
            List.of("ORDER_NUMBER_AND_EMAIL"),
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
                parseActiveSubscriptionNames(installation.get("activeSubscriptions"))
            );
        } catch (ResponseStatusException ex) {
            return new SupportState(
                fallbackScopes(installRecord),
                List.of()
            );
        }
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

    private List<String> parseActiveSubscriptionNames(Object value) {
        if (!(value instanceof List<?> items)) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            String status = optionalText(map.get("status"));
            if (status != null && !"ACTIVE".equalsIgnoreCase(status) && !"ACCEPTED".equalsIgnoreCase(status)) {
                continue;
            }
            String name = optionalText(map.get("name"));
            if (name != null) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    private boolean hasReadyWebhookTopic(ShopifyWebhookSubscriptionStatusSummary summary, String topic) {
        if (summary == null || summary.topics() == null) {
            return false;
        }
        return summary.topics().stream()
            .filter(current -> topic.equalsIgnoreCase(current.topic()))
            .map(ShopifyWebhookSubscriptionTopicStatusSummary::status)
            .anyMatch(status -> "READY".equalsIgnoreCase(status));
    }

    private boolean installRecoveryRequired(ShopifyInstallRecordSummary installRecord,
                                            ShopifyBridgeStoreSummary store) {
        return "UNINSTALLED".equalsIgnoreCase(installRecord == null ? null : installRecord.status())
            || "UNINSTALLED".equalsIgnoreCase(store == null ? null : store.installStatus());
    }

    private ShopifyBridgeStoreSummary getStoreOrNull(String shopDomain) {
        try {
            return platformShopifyStoreClient.getStore(shopDomain);
        } catch (HttpClientErrorException.NotFound ex) {
            return null;
        }
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

    private String normalizeShopDomain(String shopDomain) {
        return shopDomain == null ? "" : shopDomain.trim().toLowerCase(Locale.ROOT);
    }

    private record SupportState(
        List<String> grantedScopes,
        List<String> activeSubscriptionNames
    ) {
    }
}
