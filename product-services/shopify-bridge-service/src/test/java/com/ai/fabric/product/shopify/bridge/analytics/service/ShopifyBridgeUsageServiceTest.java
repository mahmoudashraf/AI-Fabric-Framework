package com.ai.fabric.product.shopify.bridge.analytics.service;

import com.ai.fabric.product.shopify.bridge.analytics.entity.ShopifyBridgeQueryInsightDailyEntity;
import com.ai.fabric.product.shopify.bridge.analytics.entity.ShopifyBridgeUsageDailyEntity;
import com.ai.fabric.product.shopify.bridge.analytics.repository.ShopifyBridgeQueryInsightDailyRepository;
import com.ai.fabric.product.shopify.bridge.analytics.repository.ShopifyBridgeUsageDailyRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyBridgeUsageServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recordEventCreatesOrUpdatesDailyCounter() {
        ShopifyBridgeUsageDailyRepository repository = mock(ShopifyBridgeUsageDailyRepository.class);
        ShopifyBridgeQueryInsightDailyRepository queryInsightRepository = mock(ShopifyBridgeQueryInsightDailyRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-18T12:00:00Z"), ZoneOffset.UTC);
        ShopifyBridgeUsageService service = new ShopifyBridgeUsageService(repository, queryInsightRepository, clock);

        service.recordEvent("alpha.myshopify.com", "STOREFRONT_QUERY");

        verify(repository).incrementDailyEvent(
            any(),
            eq("alpha.myshopify.com"),
            eq(LocalDate.parse("2026-04-18")),
            eq("STOREFRONT_QUERY"),
            eq(Instant.parse("2026-04-18T12:00:00Z"))
        );
    }

    @Test
    void summarizeAggregatesTodayAndLastSevenDays() {
        ShopifyBridgeUsageDailyRepository repository = mock(ShopifyBridgeUsageDailyRepository.class);
        ShopifyBridgeQueryInsightDailyRepository queryInsightRepository = mock(ShopifyBridgeQueryInsightDailyRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-18T12:00:00Z"), ZoneOffset.UTC);
        ShopifyBridgeUsageService service = new ShopifyBridgeUsageService(repository, queryInsightRepository, clock);

        ShopifyBridgeUsageDailyEntity today = row("alpha.myshopify.com", "STOREFRONT_QUERY", LocalDate.parse("2026-04-18"), 5, Instant.parse("2026-04-18T11:59:00Z"));
        ShopifyBridgeUsageDailyEntity previous = row("alpha.myshopify.com", "MERCHANT_PLAYGROUND_QUERY", LocalDate.parse("2026-04-16"), 3, Instant.parse("2026-04-16T09:00:00Z"));
        when(repository.findByShopDomainIgnoreCaseAndUsageDateGreaterThanEqualOrderByUsageDateAscEventTypeAsc(
            "alpha.myshopify.com",
            LocalDate.parse("2026-04-12")
        )).thenReturn(List.of(previous, today));
        when(queryInsightRepository.findByShopDomainIgnoreCaseAndUsageDateGreaterThanEqualOrderByUsageDateAscSurfaceIdAscSampleQueryAsc(
            "alpha.myshopify.com",
            LocalDate.parse("2026-04-12")
        )).thenReturn(List.of(
            queryRow(
                "alpha.myshopify.com",
                "ai-search",
                "STOREFRONT_QUERY",
                "show me backpacks",
                "Show me backpacks",
                LocalDate.parse("2026-04-18"),
                2,
                Instant.parse("2026-04-18T11:58:00Z")
            ),
            queryRow(
                "alpha.myshopify.com",
                "launcher",
                "STOREFRONT_QUERY",
                "what is your return policy?",
                "What is your return policy?",
                LocalDate.parse("2026-04-16"),
                4,
                Instant.parse("2026-04-16T09:05:00Z")
            )
        ));

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
        assertThat(summary.todaySurfaceUsage()).singleElement().satisfies(surface -> {
            assertThat(surface.surfaceId()).isEqualTo("ai-search");
            assertThat(surface.count()).isEqualTo(2);
        });
        assertThat(summary.last7DaySurfaceUsage()).extracting("surfaceId")
            .containsExactly("launcher", "ai-search");
        assertThat(summary.topQuestionsLast7Days()).hasSize(2);
        assertThat(summary.topQuestionsLast7Days().getFirst().queryText()).isEqualTo("What is your return policy?");
    }

    @Test
    void summarizeAllShopsAggregatesAcrossStorefronts() {
        ShopifyBridgeUsageDailyRepository repository = mock(ShopifyBridgeUsageDailyRepository.class);
        ShopifyBridgeQueryInsightDailyRepository queryInsightRepository = mock(ShopifyBridgeQueryInsightDailyRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-18T12:00:00Z"), ZoneOffset.UTC);
        ShopifyBridgeUsageService service = new ShopifyBridgeUsageService(repository, queryInsightRepository, clock);

        ShopifyBridgeUsageDailyEntity alphaToday = row("alpha.myshopify.com", "STOREFRONT_QUERY", LocalDate.parse("2026-04-18"), 5, Instant.parse("2026-04-18T11:59:00Z"));
        ShopifyBridgeUsageDailyEntity betaToday = row("beta.myshopify.com", "MERCHANT_GO_LIVE", LocalDate.parse("2026-04-18"), 2, Instant.parse("2026-04-18T10:00:00Z"));
        ShopifyBridgeUsageDailyEntity alphaPrevious = row("alpha.myshopify.com", "MERCHANT_SYNC_NOW", LocalDate.parse("2026-04-15"), 3, Instant.parse("2026-04-15T09:00:00Z"));
        when(repository.findByUsageDateGreaterThanEqualOrderByUsageDateAscShopDomainAscEventTypeAsc(LocalDate.parse("2026-04-12")))
            .thenReturn(List.of(alphaPrevious, alphaToday, betaToday));

        var summary = service.summarizeAllShops();

        assertThat(summary.activeShopsToday()).isEqualTo(2);
        assertThat(summary.activeShopsLast7Days()).isEqualTo(2);
        assertThat(summary.totalToday()).isEqualTo(7);
        assertThat(summary.totalLast7Days()).isEqualTo(10);
        assertThat(summary.lastActivityAt()).isEqualTo(Instant.parse("2026-04-18T11:59:00Z"));
        assertThat(summary.todayBreakdown()).extracting("eventType")
            .containsExactly("STOREFRONT_QUERY", "MERCHANT_GO_LIVE");
        assertThat(summary.last7DayBreakdown()).extracting("eventType")
            .containsExactly("MERCHANT_SYNC_NOW", "STOREFRONT_QUERY", "MERCHANT_GO_LIVE");
    }

    @Test
    void recordQueryInsightRedactsSensitiveFragmentsAndNormalizesSurface() throws Exception {
        ShopifyBridgeUsageDailyRepository repository = mock(ShopifyBridgeUsageDailyRepository.class);
        ShopifyBridgeQueryInsightDailyRepository queryInsightRepository = mock(ShopifyBridgeQueryInsightDailyRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-04-18T12:00:00Z"), ZoneOffset.UTC);
        ShopifyBridgeUsageService service = new ShopifyBridgeUsageService(repository, queryInsightRepository, clock);

        service.recordQueryInsight(
            "alpha.myshopify.com",
            "STOREFRONT_QUERY",
            objectMapper.readTree("""
                {
                  "query":"Track order 123456 for jane@example.com",
                  "storefrontContext":{"shopifySurfaceEntry":"product faq"}
                }
                """),
            "launcher"
        );

        verify(queryInsightRepository).incrementDailyInsight(
            any(),
            eq("alpha.myshopify.com"),
            eq(LocalDate.parse("2026-04-18")),
            eq("product-faq"),
            eq("STOREFRONT_QUERY"),
            eq("track order [number] for [email]"),
            eq("Track order [number] for [email]"),
            eq(Instant.parse("2026-04-18T12:00:00Z"))
        );
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

    private ShopifyBridgeQueryInsightDailyEntity queryRow(String shopDomain,
                                                          String surfaceId,
                                                          String eventType,
                                                          String queryKey,
                                                          String sampleQuery,
                                                          LocalDate usageDate,
                                                          long count,
                                                          Instant lastAskedAt) {
        ShopifyBridgeQueryInsightDailyEntity entity = new ShopifyBridgeQueryInsightDailyEntity();
        entity.setId("sbq-" + surfaceId);
        entity.setShopDomain(shopDomain);
        entity.setSurfaceId(surfaceId);
        entity.setEventType(eventType);
        entity.setQueryKey(queryKey);
        entity.setSampleQuery(sampleQuery);
        entity.setUsageDate(usageDate);
        entity.setQueryCount(count);
        entity.setLastQueriedAt(lastAskedAt);
        entity.setCreatedAt(lastAskedAt);
        entity.setUpdatedAt(lastAskedAt);
        return entity;
    }
}
