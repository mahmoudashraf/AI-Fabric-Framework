package com.ai.fabric.product.shopify.bridge.webhook.service;

import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreLifecycleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class ShopifyWebhookService {

    private final ShopifyBridgeStoreLifecycleService storeLifecycleService;
    private final ObjectMapper objectMapper;

    public ShopifyWebhookService(ShopifyBridgeStoreLifecycleService storeLifecycleService,
                                 ObjectMapper objectMapper) {
        this.storeLifecycleService = storeLifecycleService;
        this.objectMapper = objectMapper;
    }

    public void handle(String topic, String shopDomainHeader, String rawBody) {
        if ("app/uninstalled".equalsIgnoreCase(blankToEmpty(topic))) {
            String shopDomain = extractShopDomain(shopDomainHeader, rawBody);
            if (shopDomain != null && !shopDomain.isBlank()) {
                storeLifecycleService.markUninstalled(shopDomain);
            }
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
}
