package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ShopifyStorefrontChatService {

    private static final int MAX_CONTEXT_TEXT_LENGTH = 240;
    private static final Set<String> CONTEXT_TOP_LEVEL_FIELDS = Set.of(
        "pageType",
        "pageTitle",
        "product",
        "collection",
        "document",
        "shopifyShellModeProfile",
        "shopifySurfaceEntry",
        "shopifyPageModeGroup",
        "shopifyEffectiveConversationMode",
        "shopifyAllowedConversationModes",
        "shopifyPageModeMappings"
    );
    private static final Set<String> DEPTH_SURFACE_ENTRIES = Set.of(
        "launcher",
        "max-mode",
        "chat",
        "depth"
    );
    private static final String THINKER_MODE = "THINKER_DEEP";
    private static final Set<String> CANONICAL_CONVERSATION_MODES = Set.of(
        "navigator",
        "navigator_deep",
        "thinker",
        "thinker_deep",
        "cart_assistant",
        "executor"
    );

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyBridgeBillingService billingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ShopifyStorefrontChatService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                        ShopifyBridgeInstallCredentialService installCredentialService,
                                        ShopifyBridgeBillingService billingService) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installCredentialService = installCredentialService;
        this.billingService = billingService;
    }

    public JsonNode query(String shopDomain, JsonNode request, String shopperSessionId) {
        ShopifyBridgeStoreSummary store = requireReadyStore(shopDomain);
        ObjectNode normalizedRequest = normalizeRequest(request);
        enforceSurfaceEntitlement(store, normalizedRequest);
        applyStorefrontDepthChatMode(normalizedRequest);
        JsonNode response = platformShopifyStoreClient.queryConsumerBridgeChat(store.consumerId(), normalizedRequest, shopperSessionId);
        return shapeStorefrontResponse(normalizedRequest, response);
    }

    public JsonNode suggestions(String shopDomain, JsonNode request, String shopperSessionId) {
        ShopifyBridgeStoreSummary store = requireReadyStore(shopDomain);
        ObjectNode normalizedRequest = normalizeRequest(request);
        enforceSurfaceEntitlement(store, normalizedRequest);
        return platformShopifyStoreClient.suggestConsumerBridgeChat(store.consumerId(), normalizedRequest, shopperSessionId);
    }

    private ObjectNode normalizeRequest(JsonNode request) {
        ObjectNode body = request != null && request.isObject()
            ? (ObjectNode) request.deepCopy()
            : objectMapper.createObjectNode();
        ObjectNode storefrontContext = extractStorefrontContext(body);
        ObjectNode storefrontContextAttachment = storefrontContextAttachment(storefrontContext);
        body.remove("storefrontContext");
        CONTEXT_TOP_LEVEL_FIELDS.forEach(body::remove);
        if (storefrontContextAttachment != null && !storefrontContextAttachment.isEmpty()) {
            ArrayNode attachments = objectMapper.createArrayNode();
            JsonNode existingAttachments = body.get("attachments");
            if (existingAttachments != null && existingAttachments.isArray()) {
                attachments.addAll((ArrayNode) existingAttachments);
            }
            attachments.add(storefrontContextAttachment);
            body.set("attachments", attachments);
        }
        return body;
    }

    private ObjectNode extractStorefrontContext(ObjectNode body) {
        ObjectNode context = null;
        JsonNode rawNestedContext = body.get("storefrontContext");
        if (rawNestedContext != null && rawNestedContext.isObject()) {
            context = (ObjectNode) rawNestedContext.deepCopy();
        }
        for (String field : CONTEXT_TOP_LEVEL_FIELDS) {
            JsonNode value = body.get(field);
            if (value != null && !value.isNull()) {
                if (context == null) {
                    context = objectMapper.createObjectNode();
                }
                context.set(field, value.deepCopy());
            }
        }
        return context;
    }

    private ObjectNode storefrontContextAttachment(JsonNode rawContext) {
        if (rawContext == null || !rawContext.isObject()) {
            return null;
        }
        ObjectNode metadata = objectMapper.createObjectNode();
        copyLimitedTextField(rawContext, metadata, "pageType");
        copyLimitedTextField(rawContext, metadata, "pageTitle");
        copyLimitedTextField(rawContext, metadata, "shopifyShellModeProfile");
        copyLimitedTextField(rawContext, metadata, "shopifySurfaceEntry");
        copyLimitedTextField(rawContext, metadata, "shopifyPageModeGroup");
        copyLimitedTextField(rawContext, metadata, "shopifyEffectiveConversationMode");

        JsonNode rawProduct = rawContext.get("product");
        if (rawProduct != null && rawProduct.isObject()) {
            copyLimitedTextField(rawProduct, metadata, "id", "productId");
            copyLimitedTextField(rawProduct, metadata, "handle", "productHandle");
            copyLimitedTextField(rawProduct, metadata, "title", "productTitle");
            copyLimitedTextField(rawProduct, metadata, "vendor", "productVendor");
            copyLimitedTextField(rawProduct, metadata, "type", "productType");
            copyLimitedTextField(rawProduct, metadata, "priceCents", "productPriceCents");
        }

        JsonNode rawCollection = rawContext.get("collection");
        if (rawCollection != null && rawCollection.isObject()) {
            copyLimitedTextField(rawCollection, metadata, "id", "collectionId");
            copyLimitedTextField(rawCollection, metadata, "handle", "collectionHandle");
            copyLimitedTextField(rawCollection, metadata, "title", "collectionTitle");
        }

        JsonNode rawDocument = rawContext.get("document");
        if (rawDocument != null && rawDocument.isObject()) {
            copyLimitedTextField(rawDocument, metadata, "id", "documentId");
            copyLimitedTextField(rawDocument, metadata, "handle", "documentHandle");
            copyLimitedTextField(rawDocument, metadata, "title", "documentTitle");
            copyLimitedTextField(rawDocument, metadata, "type", "documentType");
            copyLimitedTextField(rawDocument, metadata, "url", "documentUrl");
        }

        String contentText = storefrontContextSummary(metadata);
        if (!StringUtils.hasText(contentText) && metadata.isEmpty()) {
            return null;
        }
        ObjectNode attachment = objectMapper.createObjectNode();
        attachment.put("source", "shopify-storefront-context");
        if (StringUtils.hasText(contentText)) {
            attachment.put("contentText", contentText);
        }
        if (!metadata.isEmpty()) {
            attachment.set("metadata", metadata);
        }
        return attachment;
    }

    private void copyLimitedTextField(JsonNode source, ObjectNode target, String field) {
        copyLimitedTextField(source, target, field, field);
    }

    private void copyLimitedTextField(JsonNode source, ObjectNode target, String sourceField, String targetField) {
        String value = trimToNull(textOrNull(source, sourceField));
        if (value == null) {
            return;
        }
        if (value.length() > MAX_CONTEXT_TEXT_LENGTH) {
            value = value.substring(0, MAX_CONTEXT_TEXT_LENGTH);
        }
        target.put(targetField, value);
    }

    private String storefrontContextSummary(ObjectNode metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        addSummaryPart(parts, metadata, "pageType", "Page type");
        addSummaryPart(parts, metadata, "pageTitle", "Page title");
        addSummaryPart(parts, metadata, "shopifyShellModeProfile", "Shopify shell mode");
        addSummaryPart(parts, metadata, "shopifySurfaceEntry", "Shopify surface");
        addSummaryPart(parts, metadata, "shopifyPageModeGroup", "Shopify page group");
        addSummaryPart(parts, metadata, "shopifyEffectiveConversationMode", "Shopify mode");
        addSummaryPart(parts, metadata, "productTitle", "Product");
        addSummaryPart(parts, metadata, "productHandle", "Product handle");
        addSummaryPart(parts, metadata, "productVendor", "Product vendor");
        addSummaryPart(parts, metadata, "productType", "Product type");
        addSummaryPart(parts, metadata, "productPriceCents", "Product price cents");
        addSummaryPart(parts, metadata, "collectionTitle", "Collection");
        addSummaryPart(parts, metadata, "collectionHandle", "Collection handle");
        addSummaryPart(parts, metadata, "documentTitle", "Document");
        addSummaryPart(parts, metadata, "documentType", "Document type");
        addSummaryPart(parts, metadata, "documentHandle", "Document handle");
        if (parts.isEmpty()) {
            return null;
        }
        String summary = String.join(". ", parts);
        return summary.length() > MAX_CONTEXT_TEXT_LENGTH
            ? summary.substring(0, MAX_CONTEXT_TEXT_LENGTH)
            : summary;
    }

    private void addSummaryPart(List<String> parts, ObjectNode metadata, String field, String label) {
        String value = trimToNull(textOrNull(metadata, field));
        if (value != null) {
            parts.add(label + ": " + value);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode child = node.get(field);
        return child == null || child.isNull() || !child.isValueNode() ? null : child.asText(null);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void enforceSurfaceEntitlement(ShopifyBridgeStoreSummary store, ObjectNode request) {
        ObjectNode context = storefrontContextFromAttachments(request);
        String surfaceEntry = normalizeSurfaceEntry(textOrNull(context, "shopifySurfaceEntry"));
        if (surfaceEntry == null) {
            return;
        }

        String storefrontAccessToken = storefrontAccessToken(store.shopDomain());
        ShopifyBridgeBillingSummary billingSummary = billingService.summarizeForShop(store.shopDomain(), storefrontAccessToken);
        if (DEPTH_SURFACE_ENTRIES.contains(surfaceEntry)) {
            if (billingSummary.chatFallbackEnabled()) {
                return;
            }
            throw forbidden("Companion chat depth is not available for this store's current plan.");
        }

        List<String> allowedSurfaces = effectiveAllowedSurfaces(
            billingSummary.allowedSurfaces(),
            configuredEnabledSurfaces(store)
        );
        if (!allowedSurfaces.contains(surfaceEntry)) {
            throw forbidden("Companion surface '" + surfaceEntry + "' is not available for this store's current plan.");
        }
    }

    private void applyStorefrontDepthChatMode(ObjectNode request) {
        ObjectNode context = storefrontContextFromAttachments(request);
        String surfaceEntry = normalizeSurfaceEntry(textOrNull(context, "shopifySurfaceEntry"));
        if (surfaceEntry == null || !DEPTH_SURFACE_ENTRIES.contains(surfaceEntry)) {
            return;
        }
        String requestedMode = normalizeConversationMode(textOrNull(request, "mode"));
        if (requestedMode != null) {
            request.put("mode", platformConversationMode(requestedMode));
            return;
        }
        String contextMode = normalizeConversationMode(textOrNull(context, "shopifyEffectiveConversationMode"));
        if (contextMode != null) {
            request.put("mode", platformConversationMode(contextMode));
            return;
        }
        request.put("mode", THINKER_MODE);
    }

    private String normalizeConversationMode(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        if (!CANONICAL_CONVERSATION_MODES.contains(normalized)) {
            return null;
        }
        return "thinker".equals(normalized) ? "thinker_deep" : normalized;
    }

    private String platformConversationMode(String normalizedMode) {
        return "thinker_deep".equals(normalizedMode) ? THINKER_MODE : normalizedMode;
    }

    private ObjectNode storefrontContextFromAttachments(ObjectNode request) {
        JsonNode attachments = request.get("attachments");
        if (attachments == null || !attachments.isArray()) {
            return null;
        }
        for (JsonNode attachment : attachments) {
            if (attachment == null || !attachment.isObject()) {
                continue;
            }
            if (!"shopify-storefront-context".equals(textOrNull(attachment, "source"))) {
                continue;
            }
            JsonNode metadata = attachment.get("metadata");
            return metadata != null && metadata.isObject() ? (ObjectNode) metadata : null;
        }
        return null;
    }

    private List<String> configuredEnabledSurfaces(ShopifyBridgeStoreSummary store) {
        return store.widgetDetail() != null
            && store.widgetDetail().settings() != null
            && store.widgetDetail().settings().enabledSurfaces() != null
            ? store.widgetDetail().settings().enabledSurfaces()
            : List.of();
    }

    private List<String> effectiveAllowedSurfaces(List<String> allowedSurfaces, List<String> configuredSurfaces) {
        List<String> allowed = allowedSurfaces == null ? List.of() : allowedSurfaces;
        List<String> configured = configuredSurfaces == null ? List.of() : configuredSurfaces.stream()
            .filter(StringUtils::hasText)
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .toList();
        if (configured.isEmpty()) {
            return allowed;
        }
        List<String> filtered = configured.stream()
            .filter(allowed::contains)
            .distinct()
            .toList();
        return filtered.isEmpty() ? allowed : filtered;
    }

    private String storefrontAccessToken(String shopDomain) {
        return installCredentialService.resolvePersistedMaterial(shopDomain)
            .map(ShopifyBridgeCredentialAcquisition::tokenExchangeMaterial)
            .map(material -> trimToNull(material.accessToken()))
            .orElse(null);
    }

    private String normalizeSurfaceEntry(String value) {
        String normalized = trimToNull(value);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private ShopifyBridgeStoreSummary requireReadyStore(String shopDomain) {
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
        if (!ShopifyStorefrontInteractionReadinessSupport.isReady(store)) {
            throw unavailable(firstStorefrontBlockingReason(store));
        }
        return store;
    }

    private String firstStorefrontBlockingReason(ShopifyBridgeStoreSummary store) {
        return ShopifyStorefrontInteractionReadinessSupport.firstBlockingReason(
            store,
            "Store assistant is not live yet for " + store.shopDomain() + "."
        );
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }

    private ResponseStatusException forbidden(String message) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, message);
    }

    private JsonNode shapeStorefrontResponse(ObjectNode normalizedRequest, JsonNode response) {
        String answer = extractAnswer(response);
        if (!requiresStorefrontFallback(answer)) {
            return ensureWidgetSanitizedPayload(response, answer);
        }
        String fallback = storefrontFallbackMessage(normalizedRequest);
        ObjectNode shaped = response != null && response.isObject()
            ? (ObjectNode) response.deepCopy()
            : objectMapper.createObjectNode();
        shaped.put("success", true);
        ObjectNode result = objectChild(shaped, "result");
        result.put("message", fallback);
        ObjectNode sanitizedPayload = objectChild(result, "sanitizedPayload");
        sanitizedPayload.put("type", trimToNull(textOrNull(result, "type")) == null ? "INFORMATION_PROVIDED" : textOrNull(result, "type"));
        sanitizedPayload.put("success", true);
        sanitizedPayload.put("safeSummary", fallback);
        sanitizedPayload.put("message", fallback);
        sanitizedPayload.put("answer", fallback);
        return shaped;
    }

    private JsonNode ensureWidgetSanitizedPayload(JsonNode response, String answer) {
        if (response == null || !response.isObject()) {
            return response;
        }
        ObjectNode shaped = (ObjectNode) response.deepCopy();
        ObjectNode result = objectChild(shaped, "result");
        ObjectNode sanitizedPayload = objectChild(result, "sanitizedPayload");
        String resultType = trimToNull(textOrNull(result, "type"));
        if (trimToNull(textOrNull(sanitizedPayload, "type")) == null) {
            sanitizedPayload.put("type", resultType == null ? "INFORMATION_PROVIDED" : resultType);
        }
        if (!sanitizedPayload.has("success")) {
            boolean success = result.has("success")
                ? result.path("success").asBoolean(true)
                : shaped.path("success").asBoolean(true);
            sanitizedPayload.put("success", success);
        }
        if (trimToNull(textOrNull(sanitizedPayload, "message")) == null) {
            sanitizedPayload.put("message", answer);
        }
        if (trimToNull(textOrNull(sanitizedPayload, "safeSummary")) == null) {
            sanitizedPayload.put("safeSummary", answer);
        }
        if (trimToNull(textOrNull(sanitizedPayload, "answer")) == null) {
            sanitizedPayload.put("answer", answer);
        }
        JsonNode resultData = result.get("data");
        if (!sanitizedPayload.has("data") && resultData != null && !resultData.isNull()) {
            sanitizedPayload.set("data", resultData.deepCopy());
        }
        return shaped;
    }

    private ObjectNode objectChild(ObjectNode parent, String field) {
        JsonNode existing = parent.get(field);
        if (existing != null && existing.isObject()) {
            return (ObjectNode) existing;
        }
        ObjectNode created = objectMapper.createObjectNode();
        parent.set(field, created);
        return created;
    }

    private String extractAnswer(JsonNode response) {
        for (String path : List.of(
            "result.sanitizedPayload.safeSummary",
            "result.sanitizedPayload.message",
            "result.sanitizedPayload.answer",
            "result.message",
            "message",
            "answer"
        )) {
            String value = nestedText(response, path);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String nestedText(JsonNode node, String dottedPath) {
        JsonNode current = node;
        for (String part : dottedPath.split("\\.")) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(part);
        }
        String value = current == null || current.isNull() || !current.isValueNode() ? null : current.asText(null);
        return trimToNull(value);
    }

    private boolean requiresStorefrontFallback(String answer) {
        String normalized = answer == null ? "" : answer.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank()
            || "action executed.".equals(normalized)
            || normalized.startsWith("to proceed, please provide:")
            || normalized.contains("indexed knowledge base")
            || normalized.contains("available action")
            || normalized.contains("rephrase it into a task");
    }

    private String storefrontFallbackMessage(ObjectNode normalizedRequest) {
        String query = trimToNull(textOrNull(normalizedRequest, "query"));
        ObjectNode context = storefrontContextFromAttachments(normalizedRequest);
        String surface = normalizeSurfaceEntry(textOrNull(context, "shopifySurfaceEntry"));
        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);

        if (normalizedQuery.contains("cancel") || normalizedQuery.contains("refund")) {
            return "I cannot cancel or refund an order from chat. Use the merchant support handoff for order changes, refunds, address updates, and payment questions.";
        }
        if (normalizedQuery.contains("order")) {
            return "Order help should stay with merchant support for this store unless Elite order lookup is enabled and verified. Use the store support page for order status, tracking, changes, or refunds.";
        }
        if (containsAny(normalizedQuery, "legal", "import", "medical", "allergy", "certification")) {
            if (normalizedQuery.contains("certification")) {
                return "I do not have a verified certification record for this product in the current store sources. Check the product details or contact the merchant before relying on certification claims.";
            }
            return "I cannot provide compliance, medical, or legal guidance. I can help with store products, policies, comparisons, and merchant support paths.";
        }
        if (normalizedQuery.contains("return") || "policy-strip".equals(surface)) {
            return "For return questions, review the store return policy and contact merchant support for order-specific exceptions or eligibility.";
        }
        if (normalizedQuery.contains("compare") || "comparison".equals(surface)) {
            return "I can compare store products when product details are available. Share the items or browse a collection, and I will compare fit, use case, and tradeoffs without making unsupported claims.";
        }
        if (normalizedQuery.contains("made from") || normalizedQuery.contains("material") || "product-faq".equals(surface)) {
            return "I do not have this product's exact material details in the current page context. Check the product description, selected variant details, or merchant support before relying on material claims.";
        }
        if (normalizedQuery.contains("gift") || "product-insight".equals(surface)) {
            return "This can be a good gift when the recipient's size, style, and use case match the product details. Check reviews, delivery timing, and return options before buying.";
        }
        if (normalizedQuery.contains("travel")) {
            return "For travel, look for store products that are compact, easy to carry, durable, and useful on the go. Use filters or product details to compare size, materials, and return options.";
        }
        return "I can help with store products, policies, comparisons, and support handoff. Ask about a product, collection, return policy, or shopping need.";
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
