package com.ai.fabric.product.shopify.bridge.analytics.service;

import com.ai.fabric.product.shopify.bridge.analytics.entity.ShopifyBridgeQueryInsightDailyEntity;
import com.ai.fabric.product.shopify.bridge.analytics.entity.ShopifyBridgeUsageDailyEntity;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageEventCountSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageOverview;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageRoiSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageSurfaceJourneySummary;
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
import java.util.ArrayList;
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
        Map<String, SurfaceJourneyAggregate> surfaceJourneys = new LinkedHashMap<>();
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
            SurfaceJourneyEvent surfaceJourneyEvent = surfaceJourneyEvent(row.getEventType());
            if (surfaceJourneyEvent != null) {
                SurfaceJourneyAggregate aggregate = surfaceJourneys.computeIfAbsent(
                    surfaceJourneyEvent.surfaceId,
                    ignored -> new SurfaceJourneyAggregate(surfaceJourneyEvent.surfaceId, surfaceLabel(surfaceJourneyEvent.surfaceId))
                );
                aggregate.shopperInteractions += surfaceJourneyEvent.shopperInteractions * row.getEventCount();
                aggregate.readActions += surfaceJourneyEvent.readActions * row.getEventCount();
                aggregate.governedActionGrants += surfaceJourneyEvent.governedActionGrants * row.getEventCount();
                aggregate.governedActionCompletions += surfaceJourneyEvent.governedActionCompletions * row.getEventCount();
                aggregate.governedActionFailures += surfaceJourneyEvent.governedActionFailures * row.getEventCount();
            }
        }
        for (ShopifyBridgeQueryInsightDailyEntity row : queryRows) {
            if (lastActivityAt == null || row.getLastQueriedAt().isAfter(lastActivityAt)) {
                lastActivityAt = row.getLastQueriedAt();
            }
            totalLast7Days += row.getQueryCount();
            last7dBreakdown.merge(row.getEventType(), row.getQueryCount(), Long::sum);
            if (today.equals(row.getUsageDate())) {
                totalToday += row.getQueryCount();
                todayBreakdown.merge(row.getEventType(), row.getQueryCount(), Long::sum);
            }
            if (!"STOREFRONT_QUERY".equals(row.getEventType())) {
                continue;
            }
            SurfaceJourneyAggregate surfaceJourneyAggregate = surfaceJourneys.computeIfAbsent(
                row.getSurfaceId(),
                ignored -> new SurfaceJourneyAggregate(row.getSurfaceId(), surfaceLabel(row.getSurfaceId()))
            );
            surfaceJourneyAggregate.shopperQuestions += row.getQueryCount();
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
        List<SurfaceJourneyAggregate> roiJourneys = surfaceJourneys.values().stream()
            .filter(value -> value.totalSignals() > 0)
            .sorted(Comparator
                .comparingLong(SurfaceJourneyAggregate::totalSignals).reversed()
                .thenComparing(Comparator.comparingLong((SurfaceJourneyAggregate value) -> value.governedActionCompletions).reversed())
                .thenComparing(Comparator.comparingLong((SurfaceJourneyAggregate value) -> value.readActions).reversed())
                .thenComparing(value -> value.label))
            .toList();
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
            toTopQuerySummaries(topQueries.values()),
            toTopQuerySummaries(topQueries.values().stream().filter(this::isUnansweredCandidate).toList()),
            toTopQuerySummaries(topQueries.values().stream().filter(this::isActionIntentCandidate).toList()),
            roiJourneys.stream()
                .map(value -> new ShopifyBridgeUsageSurfaceJourneySummary(
                    value.surfaceId,
                    value.label,
                    value.shopperQuestions,
                    value.shopperInteractions,
                    value.readActions,
                    value.governedActionGrants,
                    value.governedActionCompletions,
                    value.governedActionFailures
                ))
                .toList(),
            summarizeRoi(roiJourneys)
        );
    }

    @Transactional(readOnly = true)
    public ShopifyBridgeUsageOverview summarizeAllShops() {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate sevenDaysAgo = today.minusDays(6);
        List<ShopifyBridgeUsageDailyEntity> rows =
            repository.findByUsageDateGreaterThanEqualOrderByUsageDateAscShopDomainAscEventTypeAsc(sevenDaysAgo);
        List<ShopifyBridgeQueryInsightDailyEntity> queryRows =
            queryInsightRepository.findByUsageDateGreaterThanEqualOrderByUsageDateAscShopDomainAscSurfaceIdAscSampleQueryAsc(
                sevenDaysAgo
            );
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
        for (ShopifyBridgeQueryInsightDailyEntity row : queryRows) {
            activeLast7Days.add(row.getShopDomain());
            totalLast7Days += row.getQueryCount();
            last7dBreakdown.merge(row.getEventType(), row.getQueryCount(), Long::sum);
            if (today.equals(row.getUsageDate())) {
                activeToday.add(row.getShopDomain());
                totalToday += row.getQueryCount();
                todayBreakdown.merge(row.getEventType(), row.getQueryCount(), Long::sum);
            }
            if (lastActivityAt == null || row.getLastQueriedAt().isAfter(lastActivityAt)) {
                lastActivityAt = row.getLastQueriedAt();
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

    private List<ShopifyBridgeUsageTopQuerySummary> toTopQuerySummaries(Iterable<QueryAggregate> aggregates) {
        List<QueryAggregate> values = new ArrayList<>();
        aggregates.forEach(values::add);
        return values.stream()
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

    private SurfaceJourneyEvent surfaceJourneyEvent(String eventType) {
        if (!StringUtils.hasText(eventType)) {
            return null;
        }
        String normalized = eventType.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("STOREFRONT_SEARCH_SUBMITTED_")) {
            return SurfaceJourneyEvent.interaction("ai-search");
        }
        if (normalized.startsWith("STOREFRONT_CONTEXTUAL_PROMPT_CLICKED_")) {
            return SurfaceJourneyEvent.interaction("contextual-pill");
        }
        if (normalized.startsWith("STOREFRONT_PRODUCT_FAQ_CLICKED_")) {
            return SurfaceJourneyEvent.interaction("product-faq");
        }
        if (normalized.startsWith("STOREFRONT_COMPARISON_CLICKED_")) {
            return SurfaceJourneyEvent.interaction("comparison");
        }
        if (normalized.startsWith("STOREFRONT_WIDGET_OPENED_") || "STOREFRONT_CHAT_RESET".equals(normalized)) {
            return SurfaceJourneyEvent.interaction("launcher");
        }
        if ("STOREFRONT_READ_ACTION".equals(normalized)) {
            return SurfaceJourneyEvent.readAction("comparison");
        }
        if (normalized.startsWith("STOREFRONT_READ_ACTION_")) {
            return SurfaceJourneyEvent.readAction(surfaceFromEventSuffix(normalized, "STOREFRONT_READ_ACTION_"));
        }
        if (normalized.startsWith("STOREFRONT_ACTION_GRANTED_")) {
            return SurfaceJourneyEvent.actionGranted(surfaceFromEventSuffix(normalized, "STOREFRONT_ACTION_GRANTED_"));
        }
        if (normalized.startsWith("STOREFRONT_ACTION_COMPLETED_")) {
            return SurfaceJourneyEvent.actionCompleted(surfaceFromEventSuffix(normalized, "STOREFRONT_ACTION_COMPLETED_"));
        }
        if (normalized.startsWith("STOREFRONT_ACTION_FAILED_")) {
            return SurfaceJourneyEvent.actionFailed(surfaceFromEventSuffix(normalized, "STOREFRONT_ACTION_FAILED_"));
        }
        return null;
    }

    private String surfaceFromEventSuffix(String eventType, String prefix) {
        return normalizeSurfaceId(
            eventType.substring(prefix.length())
                .toLowerCase(Locale.ROOT)
                .replace('_', '-'),
            "launcher"
        );
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

    private boolean isUnansweredCandidate(QueryAggregate aggregate) {
        String query = normalizeQuestionText(aggregate.queryText);
        if (!StringUtils.hasText(query)) {
            return false;
        }
        return containsAny(query,
            "do you have",
            "do you sell",
            "show me",
            "can i",
            "can you",
            "where can",
            "how do",
            "how can",
            "what is",
            "what are",
            "which",
            "recommend",
            "available",
            "in stock",
            "size",
            "fit",
            "compatible",
            "material",
            "care",
            "warranty",
            "shipping",
            "delivery",
            "return policy",
            "compare");
    }

    private boolean isActionIntentCandidate(QueryAggregate aggregate) {
        String query = normalizeQuestionText(aggregate.queryText);
        if (!StringUtils.hasText(query)) {
            return false;
        }
        return containsAny(query,
            "order",
            "track",
            "tracking",
            "refund",
            "return",
            "exchange",
            "cancel",
            "change address",
            "edit order",
            "discount",
            "coupon",
            "apply",
            "checkout",
            "payment",
            "invoice",
            "subscription");
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeQuestionText(String value) {
        return value == null
            ? ""
            : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private ShopifyBridgeUsageRoiSummary summarizeRoi(List<SurfaceJourneyAggregate> journeys) {
        long shopperAssistSignals = journeys.stream()
            .mapToLong(value -> value.shopperQuestions + value.shopperInteractions)
            .sum();
        long decisionSupportSignals = journeys.stream()
            .mapToLong(value -> value.readActions)
            .sum();
        long governedActionGrants = journeys.stream()
            .mapToLong(value -> value.governedActionGrants)
            .sum();
        long governedActionCompletions = journeys.stream()
            .mapToLong(value -> value.governedActionCompletions)
            .sum();
        long governedActionFailures = journeys.stream()
            .mapToLong(value -> value.governedActionFailures)
            .sum();
        int activeSurfaceCount = journeys.size();
        List<String> strongestSurfaceLabels = journeys.stream()
            .limit(3)
            .map(value -> value.label)
            .toList();
        List<String> recommendations = new ArrayList<>();
        String status;
        String message;
        if (shopperAssistSignals == 0L && decisionSupportSignals == 0L && governedActionCompletions == 0L) {
            status = "NO_SIGNAL";
            message = "Companion is installed, but it has not produced enough live shopper signal yet to prove merchant value.";
        } else if (governedActionCompletions > 0L || (decisionSupportSignals >= 3L && shopperAssistSignals >= 8L && activeSurfaceCount >= 2)) {
            status = "ACTIONABLE";
            message = "Companion is producing repeat shopper guidance and decision-support evidence that is strong enough to support launch and commercial rollout claims.";
        } else if (decisionSupportSignals > 0L || shopperAssistSignals >= 5L || activeSurfaceCount >= 2) {
            status = "PROVING_VALUE";
            message = "Companion is generating credible shopper-assist and decision-support signal across real storefront surfaces.";
        } else {
            status = "EARLY_SIGNAL";
            message = "Companion has started to generate live shopper signal, but it still needs more volume or surface depth before the ROI story becomes repeatable.";
        }
        if (shopperAssistSignals == 0L) {
            recommendations.add("Drive a design-partner session or route real shopper traffic through AI search or the launcher to create live value evidence.");
        }
        if (decisionSupportSignals == 0L) {
            recommendations.add("Promote product insight, FAQ, or comparison placements on product pages so Companion proves decision support, not only discovery.");
        }
        if (activeSurfaceCount < 2) {
            recommendations.add("Activate and promote more than one embedded surface so the product reads as embedded intelligence instead of a single entry point.");
        }
        if (governedActionGrants > 0L && governedActionCompletions == 0L) {
            recommendations.add("Review governed-commerce confirmation copy so approved Elite actions convert into completed shopper outcomes.");
        }
        if (governedActionFailures > 0L) {
            recommendations.add("Investigate failed governed actions before expanding Elite claims to new stores or launch materials.");
        }
        if (governedActionCompletions > 0L) {
            recommendations.add("Use governed-commerce completions as live Elite evidence in rollout packets and merchant value proof points.");
        }
        if (recommendations.isEmpty()) {
            recommendations.add("Keep capturing top shopper questions and surface journeys so merchant value evidence stays current as traffic grows.");
        }
        if (recommendations.size() > 3) {
            recommendations = recommendations.subList(0, 3);
        }
        return new ShopifyBridgeUsageRoiSummary(
            status,
            message,
            shopperAssistSignals,
            decisionSupportSignals,
            governedActionGrants,
            governedActionCompletions,
            governedActionFailures,
            activeSurfaceCount,
            strongestSurfaceLabels,
            recommendations
        );
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

    private static final class SurfaceJourneyAggregate {
        private final String surfaceId;
        private final String label;
        private long shopperQuestions;
        private long shopperInteractions;
        private long readActions;
        private long governedActionGrants;
        private long governedActionCompletions;
        private long governedActionFailures;

        private SurfaceJourneyAggregate(String surfaceId, String label) {
            this.surfaceId = surfaceId;
            this.label = label;
        }

        private long totalSignals() {
            return shopperQuestions + shopperInteractions + readActions + governedActionGrants + governedActionCompletions + governedActionFailures;
        }
    }

    private record SurfaceJourneyEvent(
        String surfaceId,
        long shopperInteractions,
        long readActions,
        long governedActionGrants,
        long governedActionCompletions,
        long governedActionFailures
    ) {
        private static SurfaceJourneyEvent interaction(String surfaceId) {
            return new SurfaceJourneyEvent(surfaceId, 1L, 0L, 0L, 0L, 0L);
        }

        private static SurfaceJourneyEvent readAction(String surfaceId) {
            return new SurfaceJourneyEvent(surfaceId, 0L, 1L, 0L, 0L, 0L);
        }

        private static SurfaceJourneyEvent actionGranted(String surfaceId) {
            return new SurfaceJourneyEvent(surfaceId, 0L, 0L, 1L, 0L, 0L);
        }

        private static SurfaceJourneyEvent actionCompleted(String surfaceId) {
            return new SurfaceJourneyEvent(surfaceId, 0L, 0L, 0L, 1L, 0L);
        }

        private static SurfaceJourneyEvent actionFailed(String surfaceId) {
            return new SurfaceJourneyEvent(surfaceId, 0L, 0L, 0L, 0L, 1L);
        }
    }
}
