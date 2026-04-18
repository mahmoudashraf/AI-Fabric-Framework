package com.ai.fabric.product.shopify.bridge.analytics.service;

import com.ai.fabric.product.shopify.bridge.analytics.entity.ShopifyBridgeUsageDailyEntity;
import com.ai.fabric.product.shopify.bridge.analytics.repository.ShopifyBridgeUsageDailyRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyBridgeUsageServiceTest {

    @Test
    void recordEventCreatesOrUpdatesDailyCounter() {
        ShopifyBridgeUsageDailyRepository repository = mock(ShopifyBridgeUsageDailyRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-18T12:00:00Z"), ZoneOffset.UTC);
        ShopifyBridgeUsageService service = new ShopifyBridgeUsageService(repository, clock);

        when(repository.findByShopDomainIgnoreCaseAndUsageDateAndEventType(
            "alpha.myshopify.com",
            LocalDate.parse("2026-04-18"),
            "STOREFRONT_QUERY"
        )).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordEvent("alpha.myshopify.com", "STOREFRONT_QUERY");

        verify(repository).save(any(ShopifyBridgeUsageDailyEntity.class));
    }

    @Test
    void summarizeAggregatesTodayAndLastSevenDays() {
        ShopifyBridgeUsageDailyRepository repository = mock(ShopifyBridgeUsageDailyRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-18T12:00:00Z"), ZoneOffset.UTC);
        ShopifyBridgeUsageService service = new ShopifyBridgeUsageService(repository, clock);

        ShopifyBridgeUsageDailyEntity today = row("alpha.myshopify.com", "STOREFRONT_QUERY", LocalDate.parse("2026-04-18"), 5, Instant.parse("2026-04-18T11:59:00Z"));
        ShopifyBridgeUsageDailyEntity previous = row("alpha.myshopify.com", "MERCHANT_PLAYGROUND_QUERY", LocalDate.parse("2026-04-16"), 3, Instant.parse("2026-04-16T09:00:00Z"));
        when(repository.findByShopDomainIgnoreCaseAndUsageDateGreaterThanEqualOrderByUsageDateAscEventTypeAsc(
            "alpha.myshopify.com",
            LocalDate.parse("2026-04-12")
        )).thenReturn(List.of(previous, today));

        var summary = service.summarize("alpha.myshopify.com");

        assertThat(summary.totalToday()).isEqualTo(5);
        assertThat(summary.totalLast7Days()).isEqualTo(8);
        assertThat(summary.lastActivityAt()).isEqualTo(Instant.parse("2026-04-18T11:59:00Z"));
        assertThat(summary.todayBreakdown()).singleElement().satisfies(event -> {
            assertThat(event.eventType()).isEqualTo("STOREFRONT_QUERY");
            assertThat(event.count()).isEqualTo(5);
        });
        assertThat(summary.last7DayBreakdown()).extracting("eventType")
            .containsExactly("MERCHANT_PLAYGROUND_QUERY", "STOREFRONT_QUERY");
    }

    private ShopifyBridgeUsageDailyEntity row(String shopDomain,
                                              String eventType,
                                              LocalDate usageDate,
                                              long eventCount,
                                              Instant lastEventAt) {
        ShopifyBridgeUsageDailyEntity entity = new ShopifyBridgeUsageDailyEntity();
        entity.setId("sbu-" + eventType);
        entity.setShopDomain(shopDomain);
        entity.setEventType(eventType);
        entity.setUsageDate(usageDate);
        entity.setEventCount(eventCount);
        entity.setLastEventAt(lastEventAt);
        entity.setCreatedAt(lastEventAt);
        entity.setUpdatedAt(lastEventAt);
        return entity;
    }
}
