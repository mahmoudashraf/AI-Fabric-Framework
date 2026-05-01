package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeStoreBillingState;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyScopeSupport;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeCreateProvisioningJobRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordBillingStateRequest;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreSyncService;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreLifecycleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
public class ShopifyWebhookService {

    private final ShopifyBridgeStoreLifecycleService storeLifecycleService;
    private final ShopifyInstallRecordService installRecordService;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeBillingService billingService;
    private final ShopifyBridgeStoreSyncService storeSyncService;
    private final ObjectMapper objectMapper;

    public ShopifyWebhookService(ShopifyBridgeStoreLifecycleService storeLifecycleService,
                                 ShopifyInstallRecordService installRecordService,
                                 ShopifyBridgeInstallCredentialService installCredentialService,
                                 PlatformShopifyStoreClient platformShopifyStoreClient,
                                 ShopifyBridgeBillingService billingService,
                                 ShopifyBridgeStoreSyncService storeSyncService,
                                 ObjectMapper objectMapper) {
        this.storeLifecycleService = storeLifecycleService;
        this.installRecordService = installRecordService;
        this.installCredentialService = installCredentialService;
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.billingService = billingService;
        this.storeSyncService = storeSyncService;
        this.objectMapper = objectMapper;
    }

    public void handle(String topic, String shopDomainHeader, String webhookId, String rawBody) {
        String normalizedTopic = blankToEmpty(topic).toLowerCase();
        String shopDomain = extractShopDomain(shopDomainHeader, rawBody);
        if (shopDomain == null || shopDomain.isBlank()) {
            return;
        }
        JsonNode payload = parsePayload(rawBody);
        String payloadChecksum = payloadChecksum(rawBody);

        if ("app/uninstalled".equals(normalizedTopic)) {
            storeLifecycleService.markUninstalled(shopDomain);
            installCredentialService.clearLocalPersistedCredentials(shopDomain);
            installRecordService.markUninstalled(shopDomain);
            recordWebhookSafely(shopDomain, normalizedTopic, "UNINSTALLED", null, null, null, null, webhookId, payloadChecksum, null, "Shopify reported app uninstall.", false);
            return;
        }

        if ("customers/data_request".equals(normalizedTopic)) {
            recordWebhookSafely(
                shopDomain,
                normalizedTopic,
                "COMPLIANCE_DATA_REQUEST",
                "privacy",
                null,
                null,
                null,
                webhookId,
                payloadChecksum,
                null,
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
                null,
                null,
                null,
                webhookId,
                payloadChecksum,
                null,
                "Shopify requested customer redaction. The bridge service does not retain customer records locally.",
                false
            );
            return;
        }

        if ("shop/redact".equals(normalizedTopic)) {
            storeLifecycleService.markUninstalled(shopDomain);
            installCredentialService.clearLocalPersistedCredentials(shopDomain);
            installRecordService.markUninstalled(shopDomain);
            recordWebhookSafely(
                shopDomain,
                normalizedTopic,
                "COMPLIANCE_SHOP_REDACT",
                "privacy",
                null,
                null,
                null,
                webhookId,
                payloadChecksum,
                null,
                "Shopify requested shop redaction. Credentials and store mapping cleanup have been triggered.",
                false
            );
            storeLifecycleService.deleteStoreMapping(shopDomain, true);
            installRecordService.deleteRecord(shopDomain);
            return;
        }

        if ("app/scopes_update".equals(normalizedTopic)) {
            installRecordService.recordScopesUpdate(
                shopDomain,
                ShopifyScopeSupport.joinScopes(readTextArray(payload.path("current")))
            );
            recordWebhookSafely(
                shopDomain,
                normalizedTopic,
                "SCOPES_CHANGED",
                "install",
                "UPDATE",
                text(payload, "shop_id"),
                extractSourceRecordVersion(payload),
                webhookId,
                payloadChecksum,
                null,
                "Shopify app access scopes changed. Refresh support and lifecycle posture for this store.",
                false
            );
            return;
        }

        if ("app_subscriptions/update".equals(normalizedTopic)) {
            refreshBillingStateSafely(shopDomain);
        }

        WebhookImpact impact = classify(normalizedTopic);
        if (impact != null) {
            recordWebhookSafely(
                shopDomain,
                normalizedTopic,
                impact.eventType(),
                impact.sourceCategory(),
                impact.operation(),
                extractSourceObjectId(impact.sourceCategory(), payload),
                extractSourceRecordVersion(payload),
                webhookId,
                payloadChecksum,
                null,
                impact.message(),
                impact.invalidateSync()
            );
            if (impact.invalidateSync()) {
                triggerIncrementalSyncSafely(shopDomain, normalizedTopic);
            }
        }
    }

    private void refreshBillingStateSafely(String shopDomain) {
        try {
            String accessToken = platformShopifyStoreClient.resolveCredentialMaterial(shopDomain).accessToken();
            if (accessToken == null || accessToken.isBlank()) {
                return;
            }
            ShopifyBridgeStoreBillingState billingState = billingService.inspectStoreBillingState(shopDomain, accessToken);
            platformShopifyStoreClient.recordBillingState(
                shopDomain,
                new ShopifyBridgeRecordBillingStateRequest(
                    billingState.tierKey(),
                    billingState.status(),
                    null,
                    null,
                    "Shopify app subscription webhook refreshed billing state."
                )
            );
            installRecordService.recordBillingState(
                shopDomain,
                billingState.tierKey(),
                billingState.status(),
                billingState.activeSubscriptions()
            );
            enqueueProvisioningSafely(shopDomain, billingState.tierKey(), "Shopify app subscription webhook refreshed billing state.");
        } catch (RuntimeException ignored) {
            // Preserve the webhook ack path; billing posture can be recovered later from install refresh or operator checks.
        }
    }

    private void enqueueProvisioningSafely(String shopDomain, String tierKey, String reason) {
        try {
            platformShopifyStoreClient.enqueueProvisioning(
                shopDomain,
                new ShopifyBridgeCreateProvisioningJobRequest(
                    "PACKAGE_CHANGE",
                    null,
                    tierKey,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    reason,
                    false
                )
            );
        } catch (RuntimeException ignored) {
            // Provisioning is recovered by the Platform scheduler/admin retry paths; webhook delivery must remain ack-safe.
        }
    }

    private void recordWebhookSafely(String shopDomain,
                                     String topic,
                                     String eventType,
                                     String sourceCategory,
                                     String operation,
                                     String sourceObjectId,
                                     String sourceRecordVersion,
                                     String shopifyWebhookId,
                                     String payloadChecksum,
                                     Integer deliveryAttempt,
                                     String message,
                                     boolean invalidateSync) {
        try {
            storeLifecycleService.recordWebhookEvent(
                shopDomain,
                topic,
                eventType,
                sourceCategory,
                operation,
                sourceObjectId,
                sourceRecordVersion,
                shopifyWebhookId,
                payloadChecksum,
                deliveryAttempt,
                message,
                invalidateSync
            );
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
        JsonNode root = parsePayload(rawBody);
        String myshopifyDomain = text(root, "myshopify_domain");
        if (myshopifyDomain != null) {
            return myshopifyDomain.toLowerCase();
        }
        String domain = text(root, "domain");
        return domain == null ? null : domain.toLowerCase();
    }

    private JsonNode parsePayload(String rawBody) {
        try {
            return objectMapper.readTree(rawBody == null ? "{}" : rawBody);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String extractSourceObjectId(String sourceCategory, JsonNode root) {
        if ("policies".equals(sourceCategory)) {
            return "shop-policies";
        }
        String graphQlId = text(root, "admin_graphql_api_id");
        if (graphQlId != null) {
            return graphQlId;
        }
        String id = text(root, "id");
        if (id == null || id.isBlank()) {
            return null;
        }
        return id.startsWith("gid://") ? id : null;
    }

    private String extractSourceRecordVersion(JsonNode root) {
        String updatedAt = text(root, "updatedAt");
        if (updatedAt != null) {
            return updatedAt;
        }
        return text(root, "updated_at");
    }

    private String payloadChecksum(String rawBody) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((rawBody == null ? "" : rawBody).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte current : hash) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute Shopify webhook payload checksum.", ex);
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

    private java.util.List<String> readTextArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return java.util.List.of();
        }
        java.util.List<String> values = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual()) {
                continue;
            }
            String value = item.asText("").trim();
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return java.util.List.copyOf(values);
    }

    private WebhookImpact classify(String topic) {
        if (topic.startsWith("products/")) {
            return new WebhookImpact("CONTENT_CHANGED", "products", topicOperation(topic), "Shopify product content changed. Incremental sync is required.", true);
        }
        if (topic.startsWith("collections/")) {
            return new WebhookImpact("CONTENT_CHANGED", "collections", topicOperation(topic), "Shopify collection content changed. Incremental sync is required.", true);
        }
        if (topic.startsWith("pages/")) {
            return new WebhookImpact("CONTENT_CHANGED", "pages", topicOperation(topic), "Shopify page content changed. Incremental sync is required.", true);
        }
        if (topic.startsWith("articles/")) {
            return new WebhookImpact("CONTENT_CHANGED", "articles", topicOperation(topic), "Shopify article content changed. Incremental sync is required.", true);
        }
        if ("app_subscriptions/update".equals(topic)) {
            return new WebhookImpact("BILLING_CHANGED", "billing", null, "Shopify app subscription billing changed. Review merchant billing status before go-live.", false);
        }
        if ("shop/update".equals(topic)) {
            return new WebhookImpact("CONFIG_CHANGED", "policies", "UPDATE", "Shopify store configuration changed. Review policy and store metadata sync.", true);
        }
        return null;
    }

    private String topicOperation(String topic) {
        if (topic == null || topic.isBlank()) {
            return "UPDATE";
        }
        if (topic.endsWith("/create")) {
            return "CREATE";
        }
        if (topic.endsWith("/delete")) {
            return "DELETE";
        }
        return "UPDATE";
    }

    private record WebhookImpact(
        String eventType,
        String sourceCategory,
        String operation,
        String message,
        boolean invalidateSync
    ) {
    }
}
