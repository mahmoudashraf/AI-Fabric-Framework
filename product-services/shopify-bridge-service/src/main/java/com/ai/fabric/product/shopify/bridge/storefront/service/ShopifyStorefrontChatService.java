package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
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

@Service
public class ShopifyStorefrontChatService {

    private static final int MAX_CONTEXT_TEXT_LENGTH = 240;

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ShopifyStorefrontChatService(PlatformShopifyStoreClient platformShopifyStoreClient) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
    }

    public JsonNode query(String shopDomain, JsonNode request, String shopperSessionId) {
        ShopifyBridgeStoreSummary store = requireReadyStore(shopDomain);
        return platformShopifyStoreClient.queryConsumerBridgeChat(store.consumerId(), normalizeRequest(request), shopperSessionId);
    }

    public JsonNode suggestions(String shopDomain, JsonNode request, String shopperSessionId) {
        ShopifyBridgeStoreSummary store = requireReadyStore(shopDomain);
        return platformShopifyStoreClient.suggestConsumerBridgeChat(store.consumerId(), normalizeRequest(request), shopperSessionId);
    }

    private JsonNode normalizeRequest(JsonNode request) {
        ObjectNode body = request != null && request.isObject()
            ? (ObjectNode) request.deepCopy()
            : objectMapper.createObjectNode();
        ObjectNode storefrontContextAttachment = storefrontContextAttachment(body.get("storefrontContext"));
        body.remove("storefrontContext");
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

    private ObjectNode storefrontContextAttachment(JsonNode rawContext) {
        if (rawContext == null || !rawContext.isObject()) {
            return null;
        }
        ObjectNode metadata = objectMapper.createObjectNode();
        copyLimitedTextField(rawContext, metadata, "pageType");
        copyLimitedTextField(rawContext, metadata, "pageTitle");

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
        addSummaryPart(parts, metadata, "productTitle", "Product");
        addSummaryPart(parts, metadata, "productHandle", "Product handle");
        addSummaryPart(parts, metadata, "productVendor", "Product vendor");
        addSummaryPart(parts, metadata, "productType", "Product type");
        addSummaryPart(parts, metadata, "productPriceCents", "Product price cents");
        addSummaryPart(parts, metadata, "collectionTitle", "Collection");
        addSummaryPart(parts, metadata, "collectionHandle", "Collection handle");
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

    private ShopifyBridgeStoreSummary requireReadyStore(String shopDomain) {
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
        if (store.readiness() == null || !store.readiness().storefrontReady()) {
            throw unavailable(firstStorefrontBlockingReason(store));
        }
        return store;
    }

    private String firstStorefrontBlockingReason(ShopifyBridgeStoreSummary store) {
        if (store.readiness() != null && !store.readiness().storefrontBlockingReasons().isEmpty()) {
            return store.readiness().storefrontBlockingReasons().get(0);
        }
        return "Store assistant is not live yet for " + store.shopDomain() + ".";
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
