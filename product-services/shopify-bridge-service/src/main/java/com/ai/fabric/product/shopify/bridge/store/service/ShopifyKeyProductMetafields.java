package com.ai.fabric.product.shopify.bridge.store.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

final class ShopifyKeyProductMetafields {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_METAFIELDS = 8;
    private static final int MAX_VALUE_LENGTH = 320;
    private static final Set<String> RELEVANT_KEY_TOKENS = Set.of(
        "material",
        "materials",
        "fabric",
        "fit",
        "size",
        "sizing",
        "guide",
        "care",
        "dimension",
        "dimensions",
        "length",
        "width",
        "height",
        "depth",
        "weight",
        "warranty",
        "compatibility",
        "includes",
        "included",
        "origin",
        "made",
        "ingredient",
        "feature",
        "features",
        "usage"
    );

    private ShopifyKeyProductMetafields() {
    }

    static String content(Map<String, Object> productNode) {
        List<MetafieldEntry> entries = extract(productNode);
        if (entries.isEmpty()) {
            return null;
        }
        return entries.stream()
            .map(entry -> entry.label() + ": " + entry.value())
            .collect(Collectors.joining("\n", "Product details\n", ""));
    }

    static List<String> keys(Map<String, Object> productNode) {
        return extract(productNode).stream()
            .map(MetafieldEntry::qualifiedKey)
            .toList();
    }

    private static List<MetafieldEntry> extract(Map<String, Object> productNode) {
        if (productNode == null) {
            return List.of();
        }
        Object metafieldsObject = productNode.get("metafields");
        if (!(metafieldsObject instanceof Map<?, ?> metafieldsMap)) {
            return List.of();
        }
        Object edgesObject = metafieldsMap.get("edges");
        if (!(edgesObject instanceof List<?> edges)) {
            return List.of();
        }
        List<MetafieldEntry> entries = new ArrayList<>();
        for (Object edge : edges) {
            if (!(edge instanceof Map<?, ?> edgeMap)) {
                continue;
            }
            Object nodeObject = edgeMap.get("node");
            if (!(nodeObject instanceof Map<?, ?> rawNode)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> node = (Map<String, Object>) rawNode;
            MetafieldEntry entry = toEntry(node);
            if (entry != null) {
                entries.add(entry);
            }
            if (entries.size() >= MAX_METAFIELDS) {
                break;
            }
        }
        return entries;
    }

    private static MetafieldEntry toEntry(Map<String, Object> metafieldNode) {
        String namespace = text(metafieldNode.get("namespace"));
        String key = text(metafieldNode.get("key"));
        String type = text(metafieldNode.get("type"));
        String value = normalizeValue(text(metafieldNode.get("value")), type);
        if (namespace == null || key == null || value == null || !isRelevant(namespace, key, type)) {
            return null;
        }
        return new MetafieldEntry(
            namespace + "." + key,
            prettifyKey(key),
            value
        );
    }

    private static boolean isRelevant(String namespace, String key, String type) {
        String normalizedNamespace = namespace.toLowerCase(Locale.ROOT);
        String normalizedKey = key.toLowerCase(Locale.ROOT);
        String normalizedType = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (normalizedType.contains("reference")) {
            return false;
        }
        if (normalizedKey.contains("review") || normalizedKey.contains("rating")) {
            return false;
        }
        if (!normalizedNamespace.equals("custom")
            && !normalizedNamespace.equals("details")
            && !normalizedNamespace.equals("specs")
            && !normalizedNamespace.equals("facts")
            && !normalizedNamespace.equals("product")) {
            return false;
        }
        return RELEVANT_KEY_TOKENS.stream().anyMatch(normalizedKey::contains);
    }

    private static String normalizeValue(String rawValue, String type) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        String normalizedType = type == null ? "" : type.toLowerCase(Locale.ROOT);
        String normalized;
        if (normalizedType.contains("json") || normalizedType.contains("rich_text")) {
            normalized = flattenJsonText(rawValue);
        } else if (normalizedType.startsWith("list.")) {
            normalized = flattenJsonText(rawValue);
        } else {
            normalized = rawValue;
        }
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replaceAll("\\s+", " ").trim();
        if (normalized.isBlank()) {
            return null;
        }
        return normalized.length() > MAX_VALUE_LENGTH
            ? normalized.substring(0, MAX_VALUE_LENGTH) + "..."
            : normalized;
    }

    private static String flattenJsonText(String rawValue) {
        try {
            Object parsed = OBJECT_MAPPER.readValue(rawValue, Object.class);
            LinkedHashSet<String> values = new LinkedHashSet<>();
            collectText(parsed, values);
            if (values.isEmpty()) {
                return rawValue;
            }
            return String.join(", ", values);
        } catch (JsonProcessingException ex) {
            return rawValue;
        }
    }

    private static void collectText(Object value, Set<String> collector) {
        if (value instanceof String text) {
            String normalized = text.replaceAll("\\s+", " ").trim();
            if (!normalized.isBlank()) {
                collector.add(normalized);
            }
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(item -> collectText(item, collector));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            Object preferred = map.get("value");
            if (preferred instanceof String) {
                collectText(preferred, collector);
            }
            Object text = map.get("text");
            if (text instanceof String) {
                collectText(text, collector);
            }
            for (Object nested : map.values()) {
                collectText(nested, collector);
            }
        }
    }

    private static String prettifyKey(String key) {
        if (key == null || key.isBlank()) {
            return "Detail";
        }
        String normalized = key.replaceAll("[_-]+", " ").trim();
        if (normalized.isBlank()) {
            return "Detail";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private record MetafieldEntry(String qualifiedKey, String label, String value) {
    }
}
