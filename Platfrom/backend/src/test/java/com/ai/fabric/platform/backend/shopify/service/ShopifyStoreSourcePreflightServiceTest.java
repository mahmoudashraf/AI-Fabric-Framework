package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreSourcePreflightRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSourcePreflightCategorySummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyStoreSourcePreflightServiceTest {

    @Test
    void recordMarksStorePreflightReadyWhenEnabledSourcesPass() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        ShopifyStoreSourcePreflightSupport support = new ShopifyStoreSourcePreflightSupport(new ObjectMapper());

        ShopifyStoreConnectionEntity store = store();
        ShopifyStoreConnectionSummary summary = summary("PREFLIGHT_READY", "READY");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(summary);

        ShopifyStoreSourcePreflightService service = new ShopifyStoreSourcePreflightService(
            repository,
            connectionService,
            auditService,
            support
        );

        ShopifyStoreConnectionSummary result = service.record(
            "demo.myshopify.com",
            new RecordShopifyStoreSourcePreflightRequest(List.of(
                new ShopifyStoreSourcePreflightCategorySummary("products", true, "READY", 120, "Products reachable"),
                new ShopifyStoreSourcePreflightCategorySummary("collections", true, "READY", 12, "Collections reachable"),
                new ShopifyStoreSourcePreflightCategorySummary("pages", false, "READY", 0, "Disabled"),
                new ShopifyStoreSourcePreflightCategorySummary("policies", true, "READY", 4, "Policies reachable")
            ))
        );

        assertThat(result.onboardingStatus()).isEqualTo("PREFLIGHT_READY");
        assertThat(store.getSourceReadinessStatus()).isEqualTo("READY");
        assertThat(store.getOnboardingStatus()).isEqualTo("PREFLIGHT_READY");
        assertThat(store.getLastSourcePreflightAt()).isNotNull();
        assertThat(store.getDetailsJson()).contains("\"sourcePreflight\"");
    }

    @Test
    void recordMarksStoreBlockedWhenAnyEnabledCategoryIsBlocked() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        ShopifyStoreSourcePreflightSupport support = new ShopifyStoreSourcePreflightSupport(new ObjectMapper());

        ShopifyStoreConnectionEntity store = store();
        ShopifyStoreConnectionSummary summary = summary("BLOCKED", "BLOCKED");

        when(repository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(summary);

        ShopifyStoreSourcePreflightService service = new ShopifyStoreSourcePreflightService(
            repository,
            connectionService,
            auditService,
            support
        );

        ShopifyStoreConnectionSummary result = service.record(
            "demo.myshopify.com",
            new RecordShopifyStoreSourcePreflightRequest(List.of(
                new ShopifyStoreSourcePreflightCategorySummary("products", true, "BLOCKED", 0, "Products API scope missing"),
                new ShopifyStoreSourcePreflightCategorySummary("collections", true, "READY", 12, "Collections reachable")
            ))
        );

        assertThat(result.sourceReadinessStatus()).isEqualTo("BLOCKED");
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
        entity.setSourceReadinessStatus("NOT_RUN");
        entity.setWidgetStatus("NOT_ENABLED");
        entity.setOnboardingStatus("PLATFORM_BOOTSTRAPPED");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(false);
        entity.setPoliciesEnabled(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private ShopifyStoreConnectionSummary summary(String onboardingStatus, String preflightStatus) {
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
            "NOT_SYNCED",
            preflightStatus,
            "NOT_ENABLED",
            onboardingStatus,
            true,
            true,
            false,
            true,
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
