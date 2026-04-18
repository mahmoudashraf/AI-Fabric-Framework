package com.ai.fabric.product.shopify.bridge.analytics.service;

import com.ai.fabric.product.shopify.bridge.analytics.entity.ShopifyBridgeUsageDailyEntity;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageEventCountSummary;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageOverview;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageSummary;
import com.ai.fabric.product.shopify.bridge.analytics.repository.ShopifyBridgeUsageDailyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ShopifyBridgeUsageService {

    private final ShopifyBridgeUsageDailyRepository repository;
    private final Clock clock;

    @Autowired
    public ShopifyBridgeUsageService(ShopifyBridgeUsageDailyRepository repository) {
        this(repository, Clock.systemUTC());
    }

    ShopifyBridgeUsageService(ShopifyBridgeUsageDailyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public void recordEvent(String shopDomain, String eventType) {
        String normalizedShopDomain = normalizeRequired(shopDomain, "shopDomain");
        String normalizedEventType = normalizeRequired(eventType, "eventType");
        Instant now = clock.instant();
        LocalDate usageDate = LocalDate.ofInstant(now, ZoneOffset.UTC);
        ShopifyBridgeUsageDailyEntity entity = repository.findByShopDomainIgnoreCaseAndUsageDateAndEventType(
                normalizedShopDomain,
                usageDate,
                normalizedEventType
            )
            .orElseGet(() -> {
                ShopifyBridgeUsageDailyEntity created = new ShopifyBridgeUsageDailyEntity();
                created.setId("sbu-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                created.setShopDomain(normalizedShopDomain);
                created.setUsageDate(usageDate);
                created.setEventType(normalizedEventType);
                created.setEventCount(0L);
                created.setCreatedAt(now);
                return created;
            });
        entity.setEventCount(entity.getEventCount() + 1L);
        entity.setLastEventAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public ShopifyBridgeUsageSummary summarize(String shopDomain) {
        String normalizedShopDomain = normalizeRequired(shopDomain, "shopDomain");
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate sevenDaysAgo = today.minusDays(6);
        List<ShopifyBridgeUsageDailyEntity> rows =
            repository.findByShopDomainIgnoreCaseAndUsageDateGreaterThanEqualOrderByUsageDateAscEventTypeAsc(normalizedShopDomain, sevenDaysAgo);
        Map<String, Long> todayBreakdown = new LinkedHashMap<>();
        Map<String, Long> last7dBreakdown = new LinkedHashMap<>();
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
        return new ShopifyBridgeUsageSummary(
            normalizedShopDomain,
            now,
            lastActivityAt,
            totalToday,
            totalLast7Days,
            toSummaries(todayBreakdown),
            toSummaries(last7dBreakdown)
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

    private String normalizeRequired(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required.");
        }
        return value.trim().toUpperCase(Locale.ROOT).equals(value.trim()) && "eventType".equals(field)
            ? value.trim()
            : ("shopDomain".equals(field) ? value.trim().toLowerCase(Locale.ROOT) : value.trim().toUpperCase(Locale.ROOT));
    }
}
