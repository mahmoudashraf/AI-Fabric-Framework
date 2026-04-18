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
        assertThat(updated.accessTokenSecretRef()).isNull();
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

    @Test
    void recordCredentialsUpdatesSecretRefsAndExpiryMetadata() {
        ShopifyMerchantSession session = new ShopifyMerchantSession(
            "alpha.myshopify.com",
            "https://alpha.myshopify.com",
            "gid://shopify/User/1",
            Instant.parse("2026-04-18T12:00:00Z")
        );
        service.recordAuthenticatedSession(session, "embedded-host");

        ShopifyInstallRecordSummary summary = service.recordCredentials(
            "alpha.myshopify.com",
            "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA",
            "MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA_BBBBBB",
            Instant.parse("2026-04-18T01:00:00Z"),
            Instant.parse("2026-07-18T00:00:00Z"),
            "read_products"
        ).orElseThrow();

        assertThat(summary.accessTokenSecretRef()).isEqualTo("MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA");
        assertThat(summary.refreshTokenSecretRef()).isEqualTo("MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA_BBBBBB");
        assertThat(summary.scopesText()).isEqualTo("read_products");
        assertThat(summary.accessTokenExpiresAt()).isEqualTo(Instant.parse("2026-04-18T01:00:00Z"));
    }
}
