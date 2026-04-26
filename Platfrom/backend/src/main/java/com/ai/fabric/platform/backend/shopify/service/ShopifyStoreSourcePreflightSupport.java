package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSourcePreflightCategorySummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBillingStateSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreCredentialSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSupportProfileSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSourcePreflightSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSyncSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreWebhookSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreWidgetSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreWidgetSettingsSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ShopifyStoreSourcePreflightSupport {

    private static final String DEFAULT_LAUNCHER_LABEL = "Ask the store assistant";
    private static final String DEFAULT_WELCOME_MESSAGE =
        "Store assistant is ready. Ask about products, policies, or collections.";
    private static final String DEFAULT_SHELL_MODE_PROFILE = "SHOPIFY_COMPANION";
    private static final String DEFAULT_CONVERSATION_MODE = "navigator";
    private static final List<String> DEFAULT_ENABLED_SURFACES = List.of(
        "ai-search",
        "contextual-pill",
        "product-insight",
        "policy-strip",
        "product-faq",
        "comparison"
    );
    private static final List<String> DEFAULT_ALLOWED_CONVERSATION_MODES = List.of(DEFAULT_CONVERSATION_MODE);
    private static final Map<String, String> DEFAULT_PAGE_MODE_MAPPINGS = Map.of();

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
                        text(node, "message"),
                        readStringArray(node.get("signals"))
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
            JsonNode settings = widget.path("settings");
            List<String> enabledSurfaces = settings.isObject()
                ? readStringArray(settings.get("enabledSurfaces"))
                : List.of();
            List<String> allowedConversationModes = settings.isObject()
                ? readStringArray(settings.get("allowedConversationModes"))
                : List.of();
            return new ShopifyStoreWidgetSummary(
                widget.path("status").asText("UNKNOWN"),
                parseInstant(text(widget, "checkedAt")),
                text(widget, "channel"),
                text(widget, "message"),
                settings.isObject()
                    ? new ShopifyStoreWidgetSettingsSummary(
                        text(settings, "launcherLabel") == null ? DEFAULT_LAUNCHER_LABEL : text(settings, "launcherLabel"),
                        text(settings, "welcomeMessage") == null ? DEFAULT_WELCOME_MESSAGE : text(settings, "welcomeMessage"),
                        text(settings, "shellModeProfile") == null ? DEFAULT_SHELL_MODE_PROFILE : text(settings, "shellModeProfile"),
                        enabledSurfaces.isEmpty()
                            ? DEFAULT_ENABLED_SURFACES
                            : enabledSurfaces,
                        text(settings, "defaultConversationMode") == null
                            ? DEFAULT_CONVERSATION_MODE
                            : text(settings, "defaultConversationMode"),
                        allowedConversationModes.isEmpty()
                            ? DEFAULT_ALLOWED_CONVERSATION_MODES
                            : allowedConversationModes,
                        readStringMap(settings.get("pageModeMappings")).isEmpty()
                            ? DEFAULT_PAGE_MODE_MAPPINGS
                            : readStringMap(settings.get("pageModeMappings"))
                    )
                    : new ShopifyStoreWidgetSettingsSummary(
                        DEFAULT_LAUNCHER_LABEL,
                        DEFAULT_WELCOME_MESSAGE,
                        DEFAULT_SHELL_MODE_PROFILE,
                        DEFAULT_ENABLED_SURFACES,
                        DEFAULT_CONVERSATION_MODE,
                        DEFAULT_ALLOWED_CONVERSATION_MODES,
                        DEFAULT_PAGE_MODE_MAPPINGS
                    )
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

    public ShopifyStoreSupportProfileSummary summarizeSupportProfile(String detailsJson) {
        if (!hasText(detailsJson)) {
            return new ShopifyStoreSupportProfileSummary(null, null, null, null, null, false);
        }
        try {
            JsonNode root = objectMapper.readTree(detailsJson);
            JsonNode supportProfile = root.path("supportProfile");
            if (!supportProfile.isObject()) {
                return new ShopifyStoreSupportProfileSummary(null, null, null, null, null, false);
            }
            String contactEmail = text(supportProfile, "contactEmail");
            String contactUrl = text(supportProfile, "contactUrl");
            String helpCenterUrl = text(supportProfile, "helpCenterUrl");
            String orderLookupPageUrl = text(supportProfile, "orderLookupPageUrl");
            String supportPolicyNote = text(supportProfile, "supportPolicyNote");
            return new ShopifyStoreSupportProfileSummary(
                contactEmail,
                contactUrl,
                helpCenterUrl,
                orderLookupPageUrl,
                supportPolicyNote,
                hasText(contactEmail) || hasText(contactUrl) || hasText(helpCenterUrl)
            );
        } catch (Exception ex) {
            return new ShopifyStoreSupportProfileSummary(null, null, null, null, null, false);
        }
    }

    public ShopifyStoreBillingStateSummary summarizeBillingState(String shopDomain, String detailsJson) {
        if (!hasText(detailsJson)) {
            return defaultBillingState(shopDomain);
        }
        try {
            JsonNode root = objectMapper.readTree(detailsJson);
            JsonNode billingState = root.path("billingState");
            if (!billingState.isObject()) {
                return defaultBillingState(shopDomain);
            }
            return new ShopifyStoreBillingStateSummary(
                shopDomain,
                text(billingState, "tierKey") == null ? "FREE" : text(billingState, "tierKey"),
                text(billingState, "status") == null ? "ACTIVE" : text(billingState, "status"),
                text(billingState, "subscriptionId"),
                text(billingState, "subscriptionName"),
                parseInstant(text(billingState, "recordedAt")),
                text(billingState, "reason")
            );
        } catch (Exception ex) {
            return defaultBillingState(shopDomain);
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

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item != null && item.isValueNode()) {
                String value = item.asText("");
                if (!value.isBlank()) {
                    values.add(value.trim());
                }
            }
        });
        return List.copyOf(values);
    }

    private Map<String, String> readStringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (entry.getValue() != null && entry.getValue().isValueNode()) {
                String value = entry.getValue().asText("");
                if (!entry.getKey().isBlank() && !value.isBlank()) {
                    values.put(entry.getKey().trim(), value.trim());
                }
            }
        });
        return Map.copyOf(values);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private ShopifyStoreBillingStateSummary defaultBillingState(String shopDomain) {
        return new ShopifyStoreBillingStateSummary(shopDomain, "FREE", "ACTIVE", null, null, null, null);
    }
}
