package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@Service
public class ShopifyWebhookSubscriptionDiagnosticsService {

    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyWebhookSubscriptionService webhookSubscriptionService;

    public ShopifyWebhookSubscriptionDiagnosticsService(ShopifyBridgeInstallCredentialService installCredentialService,
                                                        ShopifyWebhookSubscriptionService webhookSubscriptionService) {
        this.installCredentialService = installCredentialService;
        this.webhookSubscriptionService = webhookSubscriptionService;
    }

    public ShopifyWebhookSubscriptionStatusSummary forShop(String shopDomain) {
        try {
            return installCredentialService.resolvePersistedMaterial(shopDomain)
                .map(acquisition -> webhookSubscriptionService.inspectContentSubscriptions(
                    shopDomain,
                    acquisition.tokenExchangeMaterial().accessToken()
                ))
                .orElseGet(() -> unavailable(shopDomain, "Persisted Shopify access credentials are not available yet."));
        } catch (RestClientResponseException | ResponseStatusException ex) {
            String message = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Shopify webhook subscription diagnostics failed."
                : ex.getMessage();
            return failed(shopDomain, message);
        }
    }

    public ShopifyWebhookSubscriptionStatusSummary overviewUnavailable(String shopDomain, String message) {
        return unavailable(shopDomain, message);
    }

    private ShopifyWebhookSubscriptionStatusSummary unavailable(String shopDomain, String message) {
        String webhookUri = resolveWebhookUri();
        return new ShopifyWebhookSubscriptionStatusSummary(
            normalizeShopDomain(shopDomain),
            "BLOCKED",
            message,
            webhookUri,
            webhookSubscriptionService.expectedSubscriptionCount(),
            0,
            webhookSubscriptionService.expectedSubscriptionCount(),
            0,
            Instant.now(),
            webhookSubscriptionService.expectedTopics().stream()
                .map(topic -> new com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionTopicStatusSummary(
                    topic,
                    expectedName(topic),
                    "MISSING",
                    null,
                    null,
                    null,
                    message
                ))
                .toList()
        );
    }

    private ShopifyWebhookSubscriptionStatusSummary failed(String shopDomain, String message) {
        return new ShopifyWebhookSubscriptionStatusSummary(
            normalizeShopDomain(shopDomain),
            "FAILED",
            message,
            resolveWebhookUri(),
            webhookSubscriptionService.expectedSubscriptionCount(),
            0,
            0,
            0,
            Instant.now(),
            List.of()
        );
    }

    private String expectedName(String topic) {
        return switch (topic) {
            case "APP_UNINSTALLED" -> "loom-app-uninstalled";
            case "PRODUCTS_CREATE" -> "loom-products-create";
            case "PRODUCTS_UPDATE" -> "loom-products-update";
            case "PRODUCTS_DELETE" -> "loom-products-delete";
            case "COLLECTIONS_CREATE" -> "loom-collections-create";
            case "COLLECTIONS_UPDATE" -> "loom-collections-update";
            case "COLLECTIONS_DELETE" -> "loom-collections-delete";
            case "PAGES_CREATE" -> "loom-pages-create";
            case "PAGES_UPDATE" -> "loom-pages-update";
            case "PAGES_DELETE" -> "loom-pages-delete";
            case "SHOP_UPDATE" -> "loom-shop-update";
            default -> topic == null ? null : topic.trim().toLowerCase();
        };
    }

    private String resolveWebhookUri() {
        try {
            return webhookSubscriptionService.expectedWebhookUri();
        } catch (ResponseStatusException ex) {
            return null;
        }
    }

    private String normalizeShopDomain(String shopDomain) {
        return shopDomain == null ? "" : shopDomain.trim().toLowerCase();
    }
}
