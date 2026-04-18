package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSourcePreflightCategorySummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreCredentialSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSourcePreflightSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSyncSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreWebhookSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreWidgetSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class ShopifyStoreSourcePreflightSupport {

    private final ObjectMapper objectMapper;

    public ShopifyStoreSourcePreflightSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ShopifyStoreSourcePreflightSummary summarize(String detailsJson) {
        if (!hasText(detailsJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(detailsJson);
            JsonNode sourcePreflight = root.path("sourcePreflight");
            if (!sourcePreflight.isObject()) {
                return null;
            }
            List<ShopifyStoreSourcePreflightCategorySummary> categories = sourcePreflight.path("categories").isArray()
                ? java.util.stream.StreamSupport.stream(sourcePreflight.path("categories").spliterator(), false)
                    .map(node -> new ShopifyStoreSourcePreflightCategorySummary(
                        node.path("category").asText(""),
                        node.path("enabled").asBoolean(false),
                        node.path("status").asText("UNKNOWN"),
                        node.path("itemCount").asInt(0),
                        text(node, "message")
                    ))
                    .toList()
                : List.of();
            return new ShopifyStoreSourcePreflightSummary(
                sourcePreflight.path("overallStatus").asText("UNKNOWN"),
                parseInstant(text(sourcePreflight, "checkedAt")),
                categories
            );
        } catch (Exception ex) {
            return null;
        }
    }

    public ShopifyStoreSyncSummary summarizeSync(String detailsJson) {
        if (!hasText(detailsJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(detailsJson);
            JsonNode sync = root.path("sync");
            if (!sync.isObject()) {
                return null;
            }
            return new ShopifyStoreSyncSummary(
                sync.path("status").asText("UNKNOWN"),
                parseInstant(text(sync, "checkedAt")),
                text(sync, "mode"),
                sync.path("documentCount").asInt(0),
                text(sync, "message")
            );
        } catch (Exception ex) {
            return null;
        }
    }

    public ShopifyStoreWidgetSummary summarizeWidget(String detailsJson) {
        if (!hasText(detailsJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(detailsJson);
            JsonNode widget = root.path("widget");
            if (!widget.isObject()) {
                return null;
            }
            return new ShopifyStoreWidgetSummary(
                widget.path("status").asText("UNKNOWN"),
                parseInstant(text(widget, "checkedAt")),
                text(widget, "channel"),
                text(widget, "message")
            );
        } catch (Exception ex) {
            return null;
        }
    }

    public ShopifyStoreWebhookSummary summarizeWebhook(String detailsJson) {
        if (!hasText(detailsJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(detailsJson);
            JsonNode webhook = root.path("webhook");
            if (!webhook.isObject()) {
                return null;
            }
            return new ShopifyStoreWebhookSummary(
                text(webhook, "topic"),
                text(webhook, "eventType"),
                text(webhook, "sourceCategory"),
                parseInstant(text(webhook, "receivedAt")),
                webhook.path("invalidateSync").asBoolean(false),
                text(webhook, "message")
            );
        } catch (Exception ex) {
            return null;
        }
    }

    public ShopifyStoreCredentialSummary summarizeCredentials(String detailsJson) {
        if (!hasText(detailsJson)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(detailsJson);
            JsonNode credentials = root.path("credentials");
            if (!credentials.isObject()) {
                return null;
            }
            return new ShopifyStoreCredentialSummary(
                credentials.path("status").asText("UNKNOWN"),
                hasText(text(credentials, "accessTokenSecretRef")),
                hasText(text(credentials, "refreshTokenSecretRef")),
                text(credentials, "accessTokenSecretRef"),
                text(credentials, "refreshTokenSecretRef"),
                parseInstant(text(credentials, "checkedAt")),
                parseInstant(text(credentials, "accessTokenExpiresAt")),
                parseInstant(text(credentials, "refreshTokenExpiresAt")),
                text(credentials, "scopesText"),
                credentials.path("expiring").asBoolean(false)
            );
        } catch (Exception ex) {
            return null;
        }
    }

    public ObjectNode mutableDetails(String detailsJson) {
        try {
            JsonNode parsed = hasText(detailsJson) ? objectMapper.readTree(detailsJson) : objectMapper.createObjectNode();
            return parsed.isObject() ? (ObjectNode) parsed.deepCopy() : objectMapper.createObjectNode();
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    public String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize Shopify source preflight details.", ex);
        }
    }

    public JsonNode readJsonNode(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private Instant parseInstant(String value) {
        try {
            return hasText(value) ? Instant.parse(value) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private String text(JsonNode node, String field) {
        return node.path(field).isMissingNode() ? null : (node.path(field).asText("").isBlank() ? null : node.path(field).asText("").trim());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
