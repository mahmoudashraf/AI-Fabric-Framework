package com.ai.fabric.product.shopify.bridge.analytics.repository;

import com.ai.fabric.product.shopify.bridge.analytics.entity.ShopifyBridgeUsageDailyEntity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ShopifyBridgeUsageDailyRepositoryIntegrationTest {

    @Autowired
    private ShopifyBridgeUsageDailyRepository repository;

    @Test
    void incrementDailyEventMergesIntoExistingRowOnH2() {
        LocalDate usageDate = LocalDate.parse("2026-04-19");
        Instant firstEvent = Instant.parse("2026-04-19T03:00:00Z");
        Instant secondEvent = Instant.parse("2026-04-19T03:05:00Z");

        repository.incrementDailyEvent(
            "sbu-1",
            "alpha.myshopify.com",
            usageDate,
            "STOREFRONT_QUERY",
            firstEvent
        );
        repository.incrementDailyEvent(
            "sbu-2",
            "alpha.myshopify.com",
            usageDate,
            "STOREFRONT_QUERY",
            secondEvent
        );

        ShopifyBridgeUsageDailyEntity row = repository.findByShopDomainIgnoreCaseAndUsageDateAndEventType(
                "alpha.myshopify.com",
                usageDate,
                "STOREFRONT_QUERY"
            )
            .orElseThrow();

        assertThat(row.getEventCount()).isEqualTo(2L);
        assertThat(row.getLastEventAt()).isEqualTo(secondEvent);
    }
}
