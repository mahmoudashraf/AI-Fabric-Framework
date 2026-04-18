package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreSyncService;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreLifecycleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

@Service
public class ShopifyWebhookService {

    private final ShopifyBridgeStoreLifecycleService storeLifecycleService;
    private final ShopifyInstallRecordService installRecordService;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyBridgeStoreSyncService storeSyncService;
    private final ObjectMapper objectMapper;

    public ShopifyWebhookService(ShopifyBridgeStoreLifecycleService storeLifecycleService,
                                 ShopifyInstallRecordService installRecordService,
                                 ShopifyBridgeInstallCredentialService installCredentialService,
                                 ShopifyBridgeStoreSyncService storeSyncService,
                                 ObjectMapper objectMapper) {
        this.storeLifecycleService = storeLifecycleService;
        this.installRecordService = installRecordService;
        this.installCredentialService = installCredentialService;
        this.storeSyncService = storeSyncService;
        this.objectMapper = objectMapper;
    }

    public void handle(String topic, String shopDomainHeader, String rawBody) {
        String normalizedTopic = blankToEmpty(topic).toLowerCase();
        String shopDomain = extractShopDomain(shopDomainHeader, rawBody);
        if (shopDomain == null || shopDomain.isBlank()) {
            return;
        }

        if ("app/uninstalled".equals(normalizedTopic)) {
            storeLifecycleService.markUninstalled(shopDomain);
            installCredentialService.clearPersistedCredentials(shopDomain);
            installRecordService.markUninstalled(shopDomain);
            recordWebhookSafely(shopDomain, normalizedTopic, "UNINSTALLED", null, "Shopify reported app uninstall.", false);
            return;
        }

        if ("customers/data_request".equals(normalizedTopic)) {
            recordWebhookSafely(
                shopDomain,
                normalizedTopic,
                "COMPLIANCE_DATA_REQUEST",
                "privacy",
                "Shopify requested customer data export review.",
                false
            );
            return;
        }

        if ("customers/redact".equals(normalizedTopic)) {
            recordWebhookSafely(
                shopDomain,
                normalizedTopic,
                "COMPLIANCE_CUSTOMER_REDACT",
                "privacy",
                "Shopify requested customer redaction. The bridge service does not retain customer records locally.",
                false
            );
            return;
        }

        if ("shop/redact".equals(normalizedTopic)) {
            storeLifecycleService.markUninstalled(shopDomain);
            installCredentialService.clearPersistedCredentials(shopDomain);
            installRecordService.markUninstalled(shopDomain);
            recordWebhookSafely(
                shopDomain,
                normalizedTopic,
                "COMPLIANCE_SHOP_REDACT",
                "privacy",
                "Shopify requested shop redaction. Credentials and store mapping cleanup have been triggered.",
                false
            );
            storeLifecycleService.deleteStoreMapping(shopDomain, true);
            installRecordService.deleteRecord(shopDomain);
            return;
        }

        WebhookImpact impact = classify(normalizedTopic);
        if (impact != null) {
            recordWebhookSafely(
                shopDomain,
                normalizedTopic,
                impact.eventType(),
                impact.sourceCategory(),
                impact.message(),
                impact.invalidateSync()
            );
            if (impact.invalidateSync()) {
                triggerIncrementalSyncSafely(shopDomain, normalizedTopic);
            }
        }
    }

    private void recordWebhookSafely(String shopDomain,
                                     String topic,
                                     String eventType,
                                     String sourceCategory,
                                     String message,
                                     boolean invalidateSync) {
        try {
            storeLifecycleService.recordWebhookEvent(shopDomain, topic, eventType, sourceCategory, message, invalidateSync);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() != 404) {
                throw ex;
            }
        }
    }

    private void triggerIncrementalSyncSafely(String shopDomain, String topic) {
        try {
            installCredentialService.resolvePersistedMaterial(shopDomain)
                .ifPresent(acquisition -> storeSyncService.syncFromWebhook(acquisition, topic));
        } catch (RuntimeException ignored) {
            // Preserve the webhook ack path; store state has already been invalidated for later operator recovery.
        }
    }

    private String extractShopDomain(String shopDomainHeader, String rawBody) {
        if (shopDomainHeader != null && !shopDomainHeader.isBlank()) {
            return shopDomainHeader.trim().toLowerCase();
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            String myshopifyDomain = text(root, "myshopify_domain");
            if (myshopifyDomain != null) {
                return myshopifyDomain.toLowerCase();
            }
            String domain = text(root, "domain");
            return domain == null ? null : domain.toLowerCase();
        } catch (Exception ex) {
            return null;
        }
    }

    private String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isTextual()) {
            return null;
        }
        String text = value.asText("").trim();
        return text.isBlank() ? null : text;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private WebhookImpact classify(String topic) {
        if (topic.startsWith("products/")) {
            return new WebhookImpact("CONTENT_CHANGED", "products", "Shopify product content changed. Incremental sync is required.", true);
        }
        if (topic.startsWith("collections/")) {
            return new WebhookImpact("CONTENT_CHANGED", "collections", "Shopify collection content changed. Incremental sync is required.", true);
        }
        if (topic.startsWith("pages/")) {
            return new WebhookImpact("CONTENT_CHANGED", "pages", "Shopify page content changed. Incremental sync is required.", true);
        }
        if ("shop/update".equals(topic)) {
            return new WebhookImpact("CONFIG_CHANGED", "policies", "Shopify store configuration changed. Review policy and store metadata sync.", true);
        }
        return null;
    }

    private record WebhookImpact(
        String eventType,
        String sourceCategory,
        String message,
        boolean invalidateSync
    ) {
    }
}
