package com.ai.fabric.product.shopify.bridge.analytics.service;

import com.ai.fabric.product.shopify.bridge.analytics.entity.ShopifyBridgeQueryInsightDailyEntity;
import com.ai.fabric.product.shopify.bridge.analytics.entity.ShopifyBridgeUsageDailyEntity;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageEventCountSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageOverview;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageSurfaceSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageTopQuerySummary;
import com.ai.fabric.product.shopify.bridge.analytics.repository.ShopifyBridgeQueryInsightDailyRepository;
import com.ai.fabric.product.shopify.bridge.analytics.repository.ShopifyBridgeUsageDailyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ShopifyBridgeUsageService {

    private static final int MAX_QUERY_LENGTH = 160;
    private static final int MAX_TOP_QUERIES = 5;

    private final ShopifyBridgeUsageDailyRepository repository;
    private final ShopifyBridgeQueryInsightDailyRepository queryInsightRepository;
    private final Clock clock;

    @Autowired
    public ShopifyBridgeUsageService(ShopifyBridgeUsageDailyRepository repository,
                                     ShopifyBridgeQueryInsightDailyRepository queryInsightRepository) {
        this(repository, queryInsightRepository, Clock.systemUTC());
    }

    ShopifyBridgeUsageService(ShopifyBridgeUsageDailyRepository repository,
                              ShopifyBridgeQueryInsightDailyRepository queryInsightRepository,
                              Clock clock) {
        this.repository = repository;
        this.queryInsightRepository = queryInsightRepository;
        this.clock = clock;
    }

    @Transactional
    public void recordEvent(String shopDomain, String eventType) {
        String normalizedShopDomain = normalizeRequired(shopDomain, "shopDomain");
        String normalizedEventType = normalizeRequired(eventType, "eventType");
        Instant now = clock.instant();
        LocalDate usageDate = LocalDate.ofInstant(now, ZoneOffset.UTC);
        repository.incrementDailyEvent(
            "sbu-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
            normalizedShopDomain,
            usageDate,
            normalizedEventType,
            now
        );
    }

    @Transactional
    public void recordQueryInsight(String shopDomain,
                                   String eventType,
                                   JsonNode request,
                                   String fallbackSurfaceId) {
        String normalizedShopDomain = normalizeRequired(shopDomain, "shopDomain");
        String normalizedEventType = normalizeRequired(eventType, "eventType");
        String displayQuery = sanitizeQueryDisplay(extractQueryText(request));
        if (!StringUtils.hasText(displayQuery)) {
            return;
        }
        String queryKey = normalizeQueryKey(displayQuery);
        if (!StringUtils.hasText(queryKey)) {
            return;
        }
        String surfaceId = normalizeSurfaceId(extractSurfaceId(request, fallbackSurfaceId), fallbackSurfaceId);
        Instant now = clock.instant();
        LocalDate usageDate = LocalDate.ofInstant(now, ZoneOffset.UTC);
        queryInsightRepository.incrementDailyInsight(
            "sbq-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12),
            normalizedShopDomain,
            usageDate,
            surfaceId,
            normalizedEventType,
            queryKey,
            displayQuery,
            now
        );
    }

    @Transactional(readOnly = true)
    public ShopifyBridgeUsageSummary summarize(String shopDomain) {
        String normalizedShopDomain = normalizeRequired(shopDomain, "shopDomain");
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate sevenDaysAgo = today.minusDays(6);
        List<ShopifyBridgeUsageDailyEntity> rows =
            repository.findByShopDomainIgnoreCaseAndUsageDateGreaterThanEqualOrderByUsageDateAscEventTypeAsc(normalizedShopDomain, sevenDaysAgo);
        List<ShopifyBridgeQueryInsightDailyEntity> queryRows =
            queryInsightRepository.findByShopDomainIgnoreCaseAndUsageDateGreaterThanEqualOrderByUsageDateAscSurfaceIdAscSampleQueryAsc(
                normalizedShopDomain,
                sevenDaysAgo
            );
        Map<String, Long> todayBreakdown = new LinkedHashMap<>();
        Map<String, Long> last7dBreakdown = new LinkedHashMap<>();
        Map<String, Long> todaySurfaceUsage = new LinkedHashMap<>();
        Map<String, Long> last7dSurfaceUsage = new LinkedHashMap<>();
        Map<String, QueryAggregate> topQueries = new LinkedHashMap<>();
        long totalToday = 0L;
        long totalLast7Days = 0L;
        Instant lastActivityAt = null;
        for (ShopifyBridgeUsageDailyEntity row : rows) {
            totalLast7Days += row.getEventCount();
            last7dBreakdown.merge(row.getEventType(), row.getEventCount(), Long::sum);
            if (today.equals(row.getUsageDate())) {
                totalToday += row.getEventCount();
                todayBreakdown.merge(row.getEventType(), row.getEventCount(), Long::sum);
            }
            if (lastActivityAt == null || row.getLastEventAt().isAfter(lastActivityAt)) {
                lastActivityAt = row.getLastEventAt();
            }
        }
        for (ShopifyBridgeQueryInsightDailyEntity row : queryRows) {
            if (lastActivityAt == null || row.getLastQueriedAt().isAfter(lastActivityAt)) {
                lastActivityAt = row.getLastQueriedAt();
            }
            if (!"STOREFRONT_QUERY".equals(row.getEventType())) {
                continue;
            }
            last7dSurfaceUsage.merge(row.getSurfaceId(), row.getQueryCount(), Long::sum);
            if (today.equals(row.getUsageDate())) {
                todaySurfaceUsage.merge(row.getSurfaceId(), row.getQueryCount(), Long::sum);
            }
            String aggregateKey = row.getSurfaceId() + "|" + row.getQueryKey();
            QueryAggregate aggregate = topQueries.computeIfAbsent(
                aggregateKey,
                ignored -> new QueryAggregate(row.getSurfaceId(), surfaceLabel(row.getSurfaceId()), row.getSampleQuery(), 0L, row.getLastQueriedAt())
            );
            aggregate.count += row.getQueryCount();
            if (row.getLastQueriedAt().isAfter(aggregate.lastAskedAt)) {
                aggregate.lastAskedAt = row.getLastQueriedAt();
                aggregate.queryText = row.getSampleQuery();
            }
        }
        return new ShopifyBridgeUsageSummary(
            normalizedShopDomain,
            now,
            lastActivityAt,
            totalToday,
            totalLast7Days,
            toSummaries(todayBreakdown),
            toSummaries(last7dBreakdown),
            toSurfaceSummaries(todaySurfaceUsage),
            toSurfaceSummaries(last7dSurfaceUsage),
            topQueries.values().stream()
                .sorted(Comparator
                    .comparingLong((QueryAggregate value) -> value.count).reversed()
                    .thenComparing((QueryAggregate value) -> value.lastAskedAt, Comparator.reverseOrder())
                    .thenComparing(value -> value.queryText))
                .limit(MAX_TOP_QUERIES)
                .map(value -> new ShopifyBridgeUsageTopQuerySummary(
                    value.surfaceId,
                    value.label,
                    value.queryText,
                    value.count,
                    value.lastAskedAt
                ))
                .toList()
        );
    }

    @Transactional(readOnly = true)
    public ShopifyBridgeUsageOverview summarizeAllShops() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate sevenDaysAgo = today.minusDays(6);
        List<ShopifyBridgeUsageDailyEntity> rows =
            repository.findByUsageDateGreaterThanEqualOrderByUsageDateAscShopDomainAscEventTypeAsc(sevenDaysAgo);
        Map<String, Long> todayBreakdown = new LinkedHashMap<>();
        Map<String, Long> last7dBreakdown = new LinkedHashMap<>();
        LinkedHashSet<String> activeToday = new LinkedHashSet<>();
        LinkedHashSet<String> activeLast7Days = new LinkedHashSet<>();
        long totalToday = 0L;
        long totalLast7Days = 0L;
        Instant lastActivityAt = null;
        for (ShopifyBridgeUsageDailyEntity row : rows) {
            activeLast7Days.add(row.getShopDomain());
            totalLast7Days += row.getEventCount();
            last7dBreakdown.merge(row.getEventType(), row.getEventCount(), Long::sum);
            if (today.equals(row.getUsageDate())) {
                activeToday.add(row.getShopDomain());
                totalToday += row.getEventCount();
                todayBreakdown.merge(row.getEventType(), row.getEventCount(), Long::sum);
            }
            if (lastActivityAt == null || row.getLastEventAt().isAfter(lastActivityAt)) {
                lastActivityAt = row.getLastEventAt();
            }
        }
        return new ShopifyBridgeUsageOverview(
            now,
            lastActivityAt,
            activeToday.size(),
            activeLast7Days.size(),
            totalToday,
            totalLast7Days,
            toSummaries(todayBreakdown),
            toSummaries(last7dBreakdown)
        );
    }

    private List<ShopifyBridgeUsageEventCountSummary> toSummaries(Map<String, Long> counts) {
        return counts.entrySet().stream()
            .map(entry -> new ShopifyBridgeUsageEventCountSummary(entry.getKey(), entry.getValue()))
            .toList();
    }

    private List<ShopifyBridgeUsageSurfaceSummary> toSurfaceSummaries(Map<String, Long> counts) {
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
            .map(entry -> new ShopifyBridgeUsageSurfaceSummary(entry.getKey(), surfaceLabel(entry.getKey()), entry.getValue()))
            .toList();
    }

    private String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT).equals(value.trim()) && "eventType".equals(field)
            ? value.trim()
            : ("shopDomain".equals(field) ? value.trim().toLowerCase(Locale.ROOT) : value.trim().toUpperCase(Locale.ROOT));
    }

    private String extractQueryText(JsonNode request) {
        if (request == null || !request.isObject()) {
            return null;
        }
        JsonNode query = request.get("query");
        if (query != null && query.isValueNode()) {
            return query.asText(null);
        }
        JsonNode messages = request.get("messages");
        if (messages != null && messages.isArray() && !messages.isEmpty()) {
            JsonNode first = messages.get(0);
            JsonNode content = first == null ? null : first.get("content");
            if (content != null && content.isValueNode()) {
                return content.asText(null);
            }
        }
        return null;
    }

    private String extractSurfaceId(JsonNode request, String fallbackSurfaceId) {
        if (request != null && request.isObject()) {
            JsonNode storefrontContext = request.get("storefrontContext");
            if (storefrontContext != null && storefrontContext.isObject()) {
                JsonNode surfaceEntry = storefrontContext.get("shopifySurfaceEntry");
                if (surfaceEntry != null && surfaceEntry.isValueNode()) {
                    return surfaceEntry.asText(fallbackSurfaceId);
                }
            }
        }
        return fallbackSurfaceId;
    }

    private String sanitizeQueryDisplay(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String sanitized = value
            .replaceAll("(?i)https?://\\S+", "[link]")
            .replaceAll("(?i)\\b[\\w.%+-]+@[\\w.-]+\\.[a-z]{2,}\\b", "[email]")
            .replaceAll("\\b\\d{4,}\\b", "[number]")
            .replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", " ")
            .replaceAll("\\s+", " ")
            .trim();
        if (!StringUtils.hasText(sanitized)) {
            return null;
        }
        if (sanitized.length() > MAX_QUERY_LENGTH) {
            sanitized = sanitized.substring(0, MAX_QUERY_LENGTH);
        }
        return sanitized;
    }

    private String normalizeQueryKey(String displayQuery) {
        if (!StringUtils.hasText(displayQuery)) {
            return null;
        }
        String normalized = displayQuery
            .toLowerCase(Locale.ROOT)
            .replaceAll("\\s+", " ")
            .trim();
        return normalized.length() > MAX_QUERY_LENGTH
            ? normalized.substring(0, MAX_QUERY_LENGTH)
            : normalized;
    }

    private String normalizeSurfaceId(String value, String fallbackSurfaceId) {
        String fallback = StringUtils.hasText(fallbackSurfaceId) ? fallbackSurfaceId.trim().toLowerCase(Locale.ROOT) : "launcher";
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String normalized = value.trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("(^-+|-+$)", "")
            .replaceAll("-{2,}", "-");
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private String surfaceLabel(String surfaceId) {
        return switch (normalizeSurfaceId(surfaceId, "launcher")) {
            case "ai-search" -> "AI search";
            case "contextual-pill" -> "Contextual pill";
            case "product-insight" -> "Product insight";
            case "policy-strip" -> "Policy strip";
            case "product-faq" -> "Product FAQ";
            case "comparison" -> "Comparison";
            case "merchant-playground" -> "Merchant playground";
            case "launcher" -> "Chat launcher";
            default -> surfaceId == null ? "Unknown surface" : surfaceId.replace('-', ' ');
        };
    }

    private static final class QueryAggregate {
        private final String surfaceId;
        private final String label;
        private String queryText;
        private long count;
        private Instant lastAskedAt;

        private QueryAggregate(String surfaceId, String label, String queryText, long count, Instant lastAskedAt) {
            this.surfaceId = surfaceId;
            this.label = label;
            this.queryText = queryText;
            this.count = count;
            this.lastAskedAt = lastAskedAt;
        }
    }
}
