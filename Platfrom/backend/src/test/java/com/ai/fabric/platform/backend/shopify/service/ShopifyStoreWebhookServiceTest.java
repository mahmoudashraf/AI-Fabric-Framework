package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreWebhookEventRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyStoreWebhookServiceTest {

    @Test
    void recordWebhookInvalidatesSyncWhenRequested() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        ShopifyStoreSourcePreflightSupport support = new ShopifyStoreSourcePreflightSupport(new ObjectMapper());

        ShopifyStoreConnectionEntity store = store();
        ShopifyStoreConnectionSummary summary = summary("NOT_SYNCED", "PLATFORM_BOOTSTRAPPED");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(summary);

        ShopifyStoreWebhookService service = new ShopifyStoreWebhookService(repository, connectionService, support, auditService);

        ShopifyStoreConnectionSummary result = service.record(
            "demo.myshopify.com",
            new RecordShopifyStoreWebhookEventRequest(
                "products/update",
                "CONTENT_CHANGED",
                "products",
                "Shopify product content changed. Incremental sync is required.",
                true
            )
        );

        assertThat(result.syncStatus()).isEqualTo("NOT_SYNCED");
        assertThat(store.getSyncStatus()).isEqualTo("NOT_SYNCED");
        assertThat(store.getLastWebhookAt()).isNotNull();
        assertThat(store.getDetailsJson()).contains("\"webhook\"");
        assertThat(store.getDetailsJson()).contains("\"products/update\"");
        assertThat(store.getDetailsJson()).contains("\"invalidateSync\":true");
    }

    @Test
    void recordWebhookCanPreserveSyncWhenNoInvalidationRequested() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        ShopifyStoreSourcePreflightSupport support = new ShopifyStoreSourcePreflightSupport(new ObjectMapper());

        ShopifyStoreConnectionEntity store = store();
        store.setSyncStatus("SYNCED");
        ShopifyStoreConnectionSummary summary = summary("SYNCED", "LIVE");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(summary);

        ShopifyStoreWebhookService service = new ShopifyStoreWebhookService(repository, connectionService, support, auditService);

        ShopifyStoreConnectionSummary result = service.record(
            "demo.myshopify.com",
            new RecordShopifyStoreWebhookEventRequest(
                "app/uninstalled",
                "UNINSTALLED",
                null,
                "Shopify reported app uninstall.",
                false
            )
        );

        assertThat(result.syncStatus()).isEqualTo("SYNCED");
        assertThat(store.getSyncStatus()).isEqualTo("SYNCED");
        assertThat(store.getLastWebhookAt()).isNotNull();
    }

    private ShopifyStoreConnectionEntity store() {
        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("demo.myshopify.com");
        entity.setDisplayName("Demo Shop");
        entity.setProductServiceId("psv-123");
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("SYNCED");
        entity.setSourceReadinessStatus("READY");
        entity.setWidgetStatus("NOT_ENABLED");
        entity.setOnboardingStatus("LIVE");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private ShopifyStoreConnectionSummary summary(String syncStatus, String onboardingStatus) {
        return new ShopifyStoreConnectionSummary(
            "shp-123",
            "demo.myshopify.com",
            "Demo Shop",
            "psv-123",
            "shopify-bridge-prod",
            "Shopify Bridge Service",
            "cus-123",
            "Demo Customer",
            "dep-123",
            "Shopify Companion demo.myshopify.com",
            "ACTIVE",
            "shopify-demo",
            "Demo Shop",
            "INSTALLED",
            syncStatus,
            "READY",
            "NOT_ENABLED",
            onboardingStatus,
            true,
            true,
            true,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            Instant.now(),
            Instant.now()
        );
    }
}
