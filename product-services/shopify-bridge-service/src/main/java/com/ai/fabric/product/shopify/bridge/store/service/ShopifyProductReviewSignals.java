package com.ai.fabric.product.shopify.bridge.store.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ShopifyProductReviewSignals {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Set<String> REVIEW_TOKENS = Set.of("review", "reviews", "rating", "ratings", "star");
    private static final Map<String, String> PROVIDER_LABELS = Map.of(
        "judgeme", "Judge.me",
        "stamped", "Stamped",
        "okendo", "Okendo",
        "loox", "Loox",
        "yotpo", "Yotpo",
        "spr", "Shopify Product Reviews",
        "shopify_product_reviews", "Shopify Product Reviews"
    );

    private ShopifyProductReviewSignals() {
    }

    public static List<String> supportedProviderLabels() {
        return List.copyOf(new LinkedHashSet<>(PROVIDER_LABELS.values()));
    }

    public static List<String> detectedProviderLabels(List<Map<String, Object>> productNodes) {
        if (productNodes == null || productNodes.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> labels = new LinkedHashSet<>();
        boolean reviewMetafieldsDetected = false;
        for (Map<String, Object> productNode : productNodes) {
            for (Map<String, Object> metafield : metafields(productNode)) {
                String namespace = text(metafield.get("namespace"));
                String key = text(metafield.get("key"));
                if (key == null || !looksLikeReviewMetafield(namespace, key)) {
                    continue;
                }
                reviewMetafieldsDetected = true;
                String provider = providerLabel(namespace, key);
                if (provider != null) {
                    labels.add(provider);
                }
            }
        }
        if (labels.isEmpty() && reviewMetafieldsDetected) {
            labels.add("Review metafields detected");
        }
        return List.copyOf(labels);
    }

    public static String content(Map<String, Object> productNode) {
        ReviewSignalSummary summary = summary(productNode);
        if (!summary.present()) {
            return null;
        }
        StringBuilder text = new StringBuilder("Review signals\n");
        if (summary.averageRatingText() != null && summary.reviewCount() != null) {
            text.append("Average rating: ")
                .append(summary.averageRatingText())
                .append(" / 5 from ")
                .append(summary.reviewCount())
                .append(summary.reviewCount() == 1 ? " review" : " reviews");
        } else if (summary.averageRatingText() != null) {
            text.append("Average rating: ")
                .append(summary.averageRatingText())
                .append(" / 5");
        } else if (summary.reviewCount() != null) {
            text.append(summary.reviewCount())
                .append(summary.reviewCount() == 1 ? " review" : " reviews");
        }
        if (summary.provider() != null) {
            text.append(" (").append(summary.provider()).append(")");
        }
        text.append('.');
        return text.toString();
    }

    public static ReviewSignalSummary summary(Map<String, Object> productNode) {
        if (productNode == null) {
            return ReviewSignalSummary.empty();
        }
        String provider = null;
        BigDecimal rating = null;
        Integer reviewCount = null;
        for (Map<String, Object> metafield : metafields(productNode)) {
            String namespace = text(metafield.get("namespace"));
            String key = text(metafield.get("key"));
            String type = text(metafield.get("type"));
            String value = text(metafield.get("value"));
            if (key == null || value == null || !looksLikeReviewMetafield(namespace, key)) {
                continue;
            }
            String detectedProvider = providerLabel(namespace, key);
            if (provider == null && detectedProvider != null) {
                provider = detectedProvider;
            }
            if (isCountKey(key)) {
                Integer candidateCount = extractReviewCount(value, type);
                if (candidateCount != null) {
                    reviewCount = reviewCount == null ? candidateCount : Math.max(reviewCount, candidateCount);
                }
                continue;
            }
            if (isRatingKey(key)) {
                BigDecimal candidateRating = extractAverageRating(value, type);
                if (candidateRating != null) {
                    rating = rating == null ? candidateRating : rating.max(candidateRating);
                }
            }
        }
        if (rating == null && reviewCount == null) {
            return ReviewSignalSummary.empty();
        }
        return new ReviewSignalSummary(provider, rating == null ? null : rating.stripTrailingZeros().toPlainString(), reviewCount);
    }

    private static boolean looksLikeReviewMetafield(String namespace, String key) {
        String haystack = ((namespace == null ? "" : namespace) + "." + key).toLowerCase(Locale.ROOT);
        return REVIEW_TOKENS.stream().anyMatch(haystack::contains)
            || PROVIDER_LABELS.keySet().stream().anyMatch(haystack::contains);
    }

    private static boolean isCountKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("count")
            || normalized.contains("total_reviews")
            || normalized.contains("totalratings")
            || normalized.contains("reviews_total")
            || normalized.contains("ratings_total");
    }

    private static boolean isRatingKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return (normalized.contains("rating") || normalized.contains("score") || normalized.contains("star"))
            && !isCountKey(key);
    }

    private static String providerLabel(String namespace, String key) {
        String haystack = ((namespace == null ? "" : namespace) + "." + key).toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> entry : PROVIDER_LABELS.entrySet()) {
            if (haystack.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static BigDecimal extractAverageRating(String rawValue, String type) {
        for (String scalar : scalarValues(rawValue, type)) {
            try {
                BigDecimal value = new BigDecimal(scalar.trim());
                if (value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(BigDecimal.valueOf(5)) <= 0) {
                    return value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros();
                }
            } catch (NumberFormatException ignored) {
                // Ignore non-numeric fragments.
            }
        }
        return null;
    }

    private static Integer extractReviewCount(String rawValue, String type) {
        Integer best = null;
        for (String scalar : scalarValues(rawValue, type)) {
            try {
                BigDecimal value = new BigDecimal(scalar.trim());
                if (value.scale() > 0) {
                    continue;
                }
                int parsed = value.intValueExact();
                if (parsed >= 0) {
                    best = best == null ? parsed : Math.max(best, parsed);
                }
            } catch (NumberFormatException | ArithmeticException ignored) {
                // Ignore non-integer fragments.
            }
        }
        return best;
    }

    private static List<Map<String, Object>> metafields(Map<String, Object> productNode) {
        Object metafieldsObject = productNode.get("metafields");
        if (!(metafieldsObject instanceof Map<?, ?> metafieldsMap)) {
            return List.of();
        }
        Object edgesObject = metafieldsMap.get("edges");
        if (!(edgesObject instanceof List<?> edges)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object edge : edges) {
            if (!(edge instanceof Map<?, ?> edgeMap)) {
                continue;
            }
            Object nodeObject = edgeMap.get("node");
            if (!(nodeObject instanceof Map<?, ?> nodeMap)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> node = (Map<String, Object>) nodeMap;
            items.add(node);
        }
        return List.copyOf(items);
    }

    private static List<String> scalarValues(String rawValue, String type) {
        if (rawValue == null || rawValue.isBlank()) {
            return List.of();
        }
        String normalizedType = type == null ? "" : type.toLowerCase(Locale.ROOT);
        if (!normalizedType.contains("json") && !normalizedType.contains("list")) {
            return List.of(rawValue.trim());
        }
        try {
            Object parsed = OBJECT_MAPPER.readValue(rawValue, Object.class);
            LinkedHashSet<String> values = new LinkedHashSet<>();
            collectScalars(parsed, values);
            return values.isEmpty() ? List.of(rawValue.trim()) : List.copyOf(values);
        } catch (JsonProcessingException ex) {
            return List.of(rawValue.trim());
        }
    }

    private static void collectScalars(Object value, Set<String> collector) {
        if (value instanceof Number number) {
            collector.add(number.toString());
            return;
        }
        if (value instanceof String text) {
            String normalized = text.trim();
            if (!normalized.isEmpty()) {
                collector.add(normalized);
            }
            return;
        }
        if (value instanceof List<?> list) {
            list.forEach(item -> collectScalars(item, collector));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            Object preferred = map.get("value");
            if (preferred != null) {
                collectScalars(preferred, collector);
            }
            Object count = map.get("count");
            if (count != null) {
                collectScalars(count, collector);
            }
            map.values().forEach(item -> collectScalars(item, collector));
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record ReviewSignalSummary(String provider, String averageRatingText, Integer reviewCount) {

        static ReviewSignalSummary empty() {
            return new ReviewSignalSummary(null, null, null);
        }

        public boolean present() {
            return averageRatingText != null || reviewCount != null;
        }

        Map<String, Object> metadata() {
            if (!present()) {
                return Map.of();
            }
            LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
            if (provider != null) {
                metadata.put("reviewProvider", provider);
            }
            if (averageRatingText != null) {
                metadata.put("reviewAverage", averageRatingText);
            }
            if (reviewCount != null) {
                metadata.put("reviewCount", reviewCount);
            }
            metadata.put("reviewSignalsPresent", true);
            return Map.copyOf(metadata);
        }
    }
}
