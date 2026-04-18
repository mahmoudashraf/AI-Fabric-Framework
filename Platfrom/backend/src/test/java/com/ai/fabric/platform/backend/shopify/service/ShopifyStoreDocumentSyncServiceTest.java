package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceDatasetRuntimeSyncClient;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceDatasetSyncService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreDocumentEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSyncDocument;
import com.ai.fabric.platform.backend.shopify.model.SyncShopifyStoreDocumentsRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreDocumentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStoreDocumentSyncServiceTest {

    @Test
    void syncUpsertsDocumentsDeletesStaleRecordsAndMarksStoreSynced() {
        ShopifyStoreConnectionRepository storeRepository = mock(ShopifyStoreConnectionRepository.class);
        ShopifyStoreDocumentRepository documentRepository = mock(ShopifyStoreDocumentRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        MarketplaceDatasetRuntimeSyncClient runtimeSyncClient = mock(MarketplaceDatasetRuntimeSyncClient.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        ShopifyStoreSourcePreflightSupport support = new ShopifyStoreSourcePreflightSupport(new ObjectMapper());

        ShopifyStoreConnectionEntity store = store();
        DeploymentEntity deployment = deployment();
        ShopifyStoreDocumentEntity stalePolicy = tracked("doc-policy", "support-policy", "policies");
        ShopifyStoreConnectionSummary summary = summary("SYNCED", "PLATFORM_BOOTSTRAPPED");

        when(storeRepository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(documentRepository.findByStoreConnectionIdOrderByDocumentIdAsc("shp-123")).thenReturn(List.of(stalePolicy));
        when(storeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(connectionService.getConnection("demo.myshopify.com")).thenReturn(summary);

        ShopifyStoreDocumentSyncService service = new ShopifyStoreDocumentSyncService(
            storeRepository,
            documentRepository,
            deploymentRepository,
            runtimeSyncClient,
            connectionService,
            support,
            auditService
        );

        ShopifyStoreConnectionSummary result = service.sync(
            "demo.myshopify.com",
            new SyncShopifyStoreDocumentsRequest(
                "FULL",
                List.of(
                    new ShopifyStoreSyncDocument(
                        "doc-product",
                        "products",
                        "product",
                        "Travel Backpack",
                        "Travel Backpack with 40L capacity and carry-on friendly dimensions.",
                        Map.of("handle", "travel-backpack")
                    )
                )
            )
        );

        verify(runtimeSyncClient).upsertDocuments(
            eq(deployment),
            eq("product"),
            eq("shopify-storefront:demo.myshopify.com"),
            eq("shopify-store:demo.myshopify.com"),
            any(),
            any()
        );
        verify(runtimeSyncClient, never()).deleteDocuments(
            eq(deployment),
            eq("product"),
            any(),
            any(),
            any(),
            any()
        );
        verify(runtimeSyncClient).deleteDocuments(
            eq(deployment),
            eq("support-policy"),
            eq("shopify-storefront:demo.myshopify.com"),
            eq("shopify-store:demo.myshopify.com"),
            any(),
            eq(List.of("doc-policy"))
        );
        verify(documentRepository).saveAll(any());
        verify(documentRepository).deleteByStoreConnectionIdAndDocumentIdIn("shp-123", List.of("doc-policy"));
        assertThat(store.getSyncStatus()).isEqualTo("SYNCED");
        assertThat(store.getDetailsJson()).contains("\"documentCount\":1");
        assertThat(result.syncStatus()).isEqualTo("SYNCED");
    }

    @Test
    void syncMarksStoreBlockedWhenRuntimeSyncFails() {
        ShopifyStoreConnectionRepository storeRepository = mock(ShopifyStoreConnectionRepository.class);
        ShopifyStoreDocumentRepository documentRepository = mock(ShopifyStoreDocumentRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        MarketplaceDatasetRuntimeSyncClient runtimeSyncClient = mock(MarketplaceDatasetRuntimeSyncClient.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        ShopifyStoreSourcePreflightSupport support = new ShopifyStoreSourcePreflightSupport(new ObjectMapper());

        ShopifyStoreConnectionEntity store = store();
        DeploymentEntity deployment = deployment();

        when(storeRepository.findByShopDomainIgnoreCase("demo.myshopify.com")).thenReturn(Optional.of(store));
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(documentRepository.findByStoreConnectionIdOrderByDocumentIdAsc("shp-123")).thenReturn(List.of());
        when(storeRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(runtimeSyncClient.upsertDocuments(any(), any(), any(), any(), any(), any()))
            .thenThrow(new IllegalStateException("Runtime data sync failed."));

        ShopifyStoreDocumentSyncService service = new ShopifyStoreDocumentSyncService(
            storeRepository,
            documentRepository,
            deploymentRepository,
            runtimeSyncClient,
            connectionService,
            support,
            auditService
        );

        try {
            service.sync(
                "demo.myshopify.com",
                new SyncShopifyStoreDocumentsRequest(
                    "FULL",
                    List.of(new ShopifyStoreSyncDocument("doc-product", "products", "product", "Travel Backpack", "Travel backpack", Map.of()))
                )
            );
        } catch (RuntimeException ex) {
            assertThat(ex).hasMessageContaining("Runtime data sync failed.");
        }

        assertThat(store.getSyncStatus()).isEqualTo("FAILED");
        assertThat(store.getOnboardingStatus()).isEqualTo("BLOCKED");
        assertThat(store.getDetailsJson()).contains("Runtime data sync failed.");
    }

    private ShopifyStoreConnectionEntity store() {
        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-123");
        entity.setShopDomain("demo.myshopify.com");
        entity.setDisplayName("Demo Shop");
        entity.setProductServiceId("psv-123");
        entity.setCustomerId("cus-123");
        entity.setDeploymentId("dep-123");
        entity.setConsumerId("shopify-demo");
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("NOT_SYNCED");
        entity.setSourceReadinessStatus("READY");
        entity.setWidgetStatus("NOT_ENABLED");
        entity.setOnboardingStatus("PLATFORM_BOOTSTRAPPED");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setCustomerId("cus-123");
        deployment.setTenantId("tenant-123");
        deployment.setRuntimeBaseUrl("https://runtime.example.com");
        return deployment;
    }

    private ShopifyStoreDocumentEntity tracked(String documentId, String entityType, String sourceCategory) {
        ShopifyStoreDocumentEntity entity = new ShopifyStoreDocumentEntity();
        entity.setId("shpd-123");
        entity.setStoreConnectionId("shp-123");
        entity.setDocumentId(documentId);
        entity.setEntityType(entityType);
        entity.setSourceCategory(sourceCategory);
        entity.setContentFingerprint("fingerprint");
        entity.setMetadataJson("{}");
        entity.setLastSyncedAt(Instant.now());
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
            "Demo Storefront",
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
            Instant.now(),
            Instant.now(),
            Instant.now(),
            Instant.now()
        );
    }
}
