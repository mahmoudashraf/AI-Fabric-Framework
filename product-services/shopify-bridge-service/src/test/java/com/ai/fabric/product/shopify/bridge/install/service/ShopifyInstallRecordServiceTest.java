package com.ai.fabric.product.shopify.bridge.install.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(ShopifyInstallRecordService.class)
class ShopifyInstallRecordServiceTest {

    @Autowired
    private ShopifyInstallRecordService service;

    @Test
    void recordAuthenticatedSessionCreatesAndUpdatesInstallRecord() {
        ShopifyMerchantSession session = new ShopifyMerchantSession(
            "alpha.myshopify.com",
            "https://alpha.myshopify.com",
            "gid://shopify/User/1",
            Instant.parse("2026-04-18T12:00:00Z")
        );

        ShopifyInstallRecordSummary created = service.recordAuthenticatedSession(session, "embedded-host");
        ShopifyInstallRecordSummary updated = service.recordAuthenticatedSession(session, "embedded-host");

        assertThat(created.status()).isEqualTo("INSTALLED");
        assertThat(updated.shopDomain()).isEqualTo("alpha.myshopify.com");
        assertThat(updated.appBridgeHost()).isEqualTo("embedded-host");
        assertThat(updated.lastAuthenticatedAt()).isNotNull();
    }

    @Test
    void markUninstalledUpdatesStatus() {
        ShopifyMerchantSession session = new ShopifyMerchantSession(
            "alpha.myshopify.com",
            "https://alpha.myshopify.com",
            "gid://shopify/User/1",
            Instant.parse("2026-04-18T12:00:00Z")
        );
        service.recordAuthenticatedSession(session, "embedded-host");

        ShopifyInstallRecordSummary summary = service.markUninstalled("alpha.myshopify.com").orElseThrow();

        assertThat(summary.status()).isEqualTo("UNINSTALLED");
        assertThat(summary.lastUninstalledAt()).isNotNull();
    }
}
