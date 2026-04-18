package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSourcePreflightCategorySummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSourcePreflightSummary;
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
