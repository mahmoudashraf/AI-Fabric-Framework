package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreSyncStatusRequest;
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

class ShopifyStoreSyncServiceTest {

    @Test
    void recordWritesSyncDetailAndTimestamp() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        ShopifyStoreSourcePreflightSupport support = new ShopifyStoreSourcePreflightSupport(new ObjectMapper());

        ShopifyStoreConnectionEntity store = store();
        ShopifyStoreConnectionSummary summary = summary("PREFLIGHT_READY", "SYNCED");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(summary);

        ShopifyStoreSyncService service = new ShopifyStoreSyncService(repository, connectionService, auditService, support);

        ShopifyStoreConnectionSummary result = service.record(
            "demo.myshopify.com",
            new RecordShopifyStoreSyncStatusRequest("SYNCED", "FULL", 128, "Initial import completed")
        );

        assertThat(result.syncStatus()).isEqualTo("SYNCED");
        assertThat(store.getSyncStatus()).isEqualTo("SYNCED");
        assertThat(store.getLastSyncAt()).isNotNull();
        assertThat(store.getDetailsJson()).contains("\"sync\"");
        assertThat(store.getDetailsJson()).contains("\"documentCount\":128");
    }

    @Test
    void recordMarksBlockedWhenSyncFails() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        ShopifyStoreSourcePreflightSupport support = new ShopifyStoreSourcePreflightSupport(new ObjectMapper());

        ShopifyStoreConnectionEntity store = store();
        ShopifyStoreConnectionSummary summary = summary("BLOCKED", "FAILED");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(summary);

        ShopifyStoreSyncService service = new ShopifyStoreSyncService(repository, connectionService, auditService, support);

        ShopifyStoreConnectionSummary result = service.record(
            "demo.myshopify.com",
            new RecordShopifyStoreSyncStatusRequest("FAILED", "DELTA", 0, "Shopify API throttled")
        );

        assertThat(result.syncStatus()).isEqualTo("FAILED");
        assertThat(store.getOnboardingStatus()).isEqualTo("BLOCKED");
    }

    private ShopifyStoreConnectionEntity store() {
        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("demo.myshopify.com");
        entity.setDisplayName("Demo Shop");
        entity.setProductServiceId("psv-123");
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("NOT_SYNCED");
        entity.setSourceReadinessStatus("READY");
        entity.setWidgetStatus("NOT_ENABLED");
        entity.setOnboardingStatus("PREFLIGHT_READY");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private ShopifyStoreConnectionSummary summary(String onboardingStatus, String syncStatus) {
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
            "DRAFT",
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
            null,
            Instant.now(),
            null,
            null,
            Instant.now(),
            Instant.now()
        );
    }
}
