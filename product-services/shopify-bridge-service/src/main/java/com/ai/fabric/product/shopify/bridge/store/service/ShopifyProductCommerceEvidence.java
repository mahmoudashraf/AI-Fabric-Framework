package com.ai.fabric.product.shopify.bridge.store.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ShopifyProductCommerceEvidence {

    private static final int MAX_VARIANT_SUMMARY_ITEMS = 8;

    private ShopifyProductCommerceEvidence() {
    }

    static String content(Map<String, Object> product) {
        Evidence evidence = summarize(product);
        List<String> parts = new ArrayList<>();
        if (evidence.priceRange() != null) {
            parts.add("Price range: " + evidence.priceRange() + ".");
        }
        if (evidence.totalInventory() != null) {
            parts.add("Total inventory: " + evidence.totalInventory() + ".");
        }
        if (evidence.availability() != null) {
            parts.add("Availability: " + evidence.availability() + ".");
        }
        if (evidence.variantSummary() != null) {
            parts.add("Variant details: " + evidence.variantSummary() + ".");
        }
        return parts.isEmpty() ? null : String.join("\n\n", parts);
    }

    static Map<String, Object> metadata(Map<String, Object> product) {
        Evidence evidence = summarize(product);
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        put(metadata, "minPrice", evidence.minPrice());
        put(metadata, "maxPrice", evidence.maxPrice());
        put(metadata, "currencyCode", evidence.currencyCode());
        put(metadata, "priceRange", evidence.priceRange());
        put(metadata, "totalInventory", evidence.totalInventory());
        put(metadata, "availability", evidence.availability());
        put(metadata, "variantCount", evidence.variantCount());
        put(metadata, "availableVariantCount", evidence.availableVariantCount());
        put(metadata, "variantSummary", evidence.variantSummary());
        put(metadata, "product_variant_id", evidence.productVariantId());
        put(metadata, "firstAvailableVariantTitle", evidence.productVariantTitle());
        return Map.copyOf(metadata);
    }

    static String priceRange(Map<String, Object> product) {
        return summarize(product).priceRange();
    }

    static String currencyCode(Map<String, Object> product) {
        return summarize(product).currencyCode();
    }

    static String availability(Map<String, Object> product) {
        return summarize(product).availability();
    }

    static Integer totalInventory(Map<String, Object> product) {
        return summarize(product).totalInventory();
    }

    static Integer availableVariantCount(Map<String, Object> product) {
        return summarize(product).availableVariantCount();
    }

    static String variantSummary(Map<String, Object> product) {
        return summarize(product).variantSummary();
    }

    static Integer variantCount(Map<String, Object> product) {
        return summarize(product).variantCount();
    }

    static String productVariantId(Map<String, Object> product) {
        return summarize(product).productVariantId();
    }

    static String productVariantTitle(Map<String, Object> product) {
        return summarize(product).productVariantTitle();
    }

    private static Evidence summarize(Map<String, Object> product) {
        PriceRange priceRange = priceRangeFrom(product);
        List<Map<String, Object>> variants = variantNodes(product);
        Integer totalInventory = product == null ? null : integer(product.get("totalInventory"));
        int availableCount = 0;
        boolean sawAvailability = false;
        List<String> variantSummaries = new ArrayList<>();
        Map<String, Object> firstVariant = null;
        Map<String, Object> firstAvailableVariant = null;
        for (Map<String, Object> variant : variants) {
            if (firstVariant == null && text(variant.get("id")) != null) {
                firstVariant = variant;
            }
            Boolean available = bool(variant.get("availableForSale"));
            if (available != null) {
                sawAvailability = true;
                if (available) {
                    availableCount++;
                    if (firstAvailableVariant == null && text(variant.get("id")) != null) {
                        firstAvailableVariant = variant;
                    }
                }
            }
            if (variantSummaries.size() < MAX_VARIANT_SUMMARY_ITEMS) {
                String summary = variantSummary(variant, priceRange.currencyCode());
                if (summary != null) {
                    variantSummaries.add(summary);
                }
            }
        }
        String availability = availability(variants.size(), availableCount, sawAvailability);
        String variantSummary = variantSummaries.isEmpty() ? null : String.join("; ", variantSummaries);
        if (variantSummary != null && variants.size() > MAX_VARIANT_SUMMARY_ITEMS) {
            variantSummary += "; " + (variants.size() - MAX_VARIANT_SUMMARY_ITEMS) + " more variants not shown";
        }
        Map<String, Object> selectedVariant = firstAvailableVariant != null ? firstAvailableVariant : firstVariant;
        return new Evidence(
            priceRange.minPrice(),
            priceRange.maxPrice(),
            priceRange.currencyCode(),
            priceRange.display(),
            totalInventory,
            availability,
            variants.isEmpty() ? null : variants.size(),
            sawAvailability ? availableCount : null,
            variantSummary,
            selectedVariant == null ? null : text(selectedVariant.get("id")),
            selectedVariant == null ? null : variantTitle(selectedVariant)
        );
    }

    @SuppressWarnings("unchecked")
    private static PriceRange priceRangeFrom(Map<String, Object> product) {
        Object value = product == null ? null : product.get("priceRangeV2");
        if (!(value instanceof Map<?, ?> range)) {
            return new PriceRange(null, null, null, null);
        }
        Map<String, Object> min = range.get("minVariantPrice") instanceof Map<?, ?> minMap
            ? (Map<String, Object>) minMap
            : Map.of();
        Map<String, Object> max = range.get("maxVariantPrice") instanceof Map<?, ?> maxMap
            ? (Map<String, Object>) maxMap
            : Map.of();
        String minAmount = text(min.get("amount"));
        String maxAmount = text(max.get("amount"));
        String minCurrency = text(min.get("currencyCode"));
        String maxCurrency = text(max.get("currencyCode"));
        String currency = minCurrency != null ? minCurrency : maxCurrency;
        if (minAmount == null && maxAmount == null) {
            return new PriceRange(null, null, currency, null);
        }
        if (minAmount != null && minAmount.equals(maxAmount) && sameCurrency(minCurrency, maxCurrency)) {
            return new PriceRange(minAmount, maxAmount, currency, withCurrency(minAmount, currency));
        }
        String display;
        if (minAmount != null && maxAmount != null) {
            display = withCurrency(minAmount, minCurrency != null ? minCurrency : currency)
                + " to "
                + withCurrency(maxAmount, maxCurrency != null ? maxCurrency : currency);
        } else {
            display = withCurrency(minAmount != null ? minAmount : maxAmount, currency);
        }
        return new PriceRange(minAmount, maxAmount, currency, display);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> variantNodes(Map<String, Object> product) {
        if (product == null || !(product.get("variants") instanceof Map<?, ?> connection)) {
            return List.of();
        }
        Object edgesValue = connection.get("edges");
        if (!(edgesValue instanceof List<?> edges) || edges.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Object edgeValue : edges) {
            if (!(edgeValue instanceof Map<?, ?> edge) || !(edge.get("node") instanceof Map<?, ?> node)) {
                continue;
            }
            nodes.add((Map<String, Object>) node);
        }
        return nodes;
    }

    private static String variantSummary(Map<String, Object> variant, String fallbackCurrency) {
        List<String> parts = new ArrayList<>();
        parts.add(variantTitle(variant));
        String sku = text(variant.get("sku"));
        if (sku != null) {
            parts.add("SKU " + sku);
        }
        String price = money(variant.get("price"), fallbackCurrency);
        if (price != null) {
            parts.add("price " + price);
        }
        String compareAtPrice = money(variant.get("compareAtPrice"), fallbackCurrency);
        if (compareAtPrice != null) {
            parts.add("compare-at " + compareAtPrice);
        }
        Boolean available = bool(variant.get("availableForSale"));
        if (available != null) {
            parts.add(available ? "available" : "not currently available");
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private static String variantTitle(Map<String, Object> variant) {
        String title = text(variant.get("title"));
        String optionSummary = optionSummary(variant.get("selectedOptions"));
        if (title != null && !"default title".equals(title.toLowerCase(Locale.ROOT))) {
            return title;
        }
        if (optionSummary != null) {
            return optionSummary;
        }
        return "Default variant";
    }

    @SuppressWarnings("unchecked")
    private static String optionSummary(Object value) {
        if (!(value instanceof List<?> options) || options.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Object optionValue : options) {
            if (!(optionValue instanceof Map<?, ?> option)) {
                continue;
            }
            String name = text(option.get("name"));
            String optionText = text(option.get("value"));
            if ("title".equalsIgnoreCase(name) && "default title".equalsIgnoreCase(optionText)) {
                continue;
            }
            if (name != null && optionText != null) {
                parts.add(name + ": " + optionText);
            } else if (optionText != null) {
                parts.add(optionText);
            }
        }
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    @SuppressWarnings("unchecked")
    private static String money(Object value, String fallbackCurrency) {
        if (value instanceof Map<?, ?> map) {
            String amount = text(map.get("amount"));
            String currency = text(map.get("currencyCode"));
            return amount == null ? null : withCurrency(amount, currency != null ? currency : fallbackCurrency);
        }
        String amount = text(value);
        return amount == null ? null : withCurrency(amount, fallbackCurrency);
    }

    private static String availability(int variantCount, int availableCount, boolean sawAvailability) {
        if (!sawAvailability || variantCount <= 0) {
            return null;
        }
        if (variantCount == 1) {
            return availableCount == 1 ? "available" : "not currently available";
        }
        return availableCount + " of " + variantCount + " variants available";
    }

    private static boolean sameCurrency(String left, String right) {
        return left == null || right == null || left.equals(right);
    }

    private static String withCurrency(String amount, String currency) {
        if (amount == null) {
            return null;
        }
        return currency == null ? amount : amount + " " + currency;
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private static Boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if ("true".equalsIgnoreCase(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text)) {
            return false;
        }
        return null;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = text(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static void put(Map<String, Object> metadata, String key, Object value) {
        if (value instanceof String text && text.isBlank()) {
            return;
        }
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private record PriceRange(String minPrice, String maxPrice, String currencyCode, String display) {
    }

    private record Evidence(String minPrice,
                            String maxPrice,
                            String currencyCode,
                            String priceRange,
                            Integer totalInventory,
                            String availability,
                            Integer variantCount,
                            Integer availableVariantCount,
                            String variantSummary,
                            String productVariantId,
                            String productVariantTitle) {
    }
}
