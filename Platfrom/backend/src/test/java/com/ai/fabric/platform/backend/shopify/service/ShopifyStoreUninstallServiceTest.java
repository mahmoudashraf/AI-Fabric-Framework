package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStoreUninstallServiceTest {

    @Test
    void markUninstalledDisablesAndUnbindsConsumer() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        PlatformCustomerConsumerService customerConsumerService = mock(PlatformCustomerConsumerService.class);
        ShopifyStoreCredentialService credentialService = mock(ShopifyStoreCredentialService.class);
        ShopifyStoreDocumentSyncService documentSyncService = mock(ShopifyStoreDocumentSyncService.class);

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ShopifyStoreSourcePreflightSupport support = new ShopifyStoreSourcePreflightSupport(objectMapper);

        PlatformManagedProductServiceEntity productService = new PlatformManagedProductServiceEntity();
        productService.setId("ps-1");
        productService.setServiceRef("shopify-bridge-prod");
        productService.setDisplayName("Shopify Bridge");
        productService.setProductFamily("SHOPIFY");
        productService.setServiceKind("SHOPIFY_BRIDGE_SERVICE");
        when(productServiceService.requireServiceById("ps-1")).thenReturn(productService);

        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-1");
        entity.setShopDomain("alpha.myshopify.com");
        entity.setDisplayName("Alpha");
        entity.setProductServiceId("ps-1");
        entity.setCustomerId("cust-1");
        entity.setDeploymentId("dep-1");
        entity.setConsumerId("consumer-alpha");
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("SYNCED");
        entity.setSourceReadinessStatus("READY");
        entity.setWidgetStatus("ENABLED");
        entity.setOnboardingStatus("LIVE");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setCreatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-18T10:00:00Z"));

        PlatformConsumerEntity consumer = new PlatformConsumerEntity();
        consumer.setId("con-1");
        consumer.setCustomerId("cust-1");
        consumer.setConsumerId("consumer-alpha");
        consumer.setDisplayName("Alpha Storefront");
        consumer.setDescription("Shopper-facing companion");
        consumer.setStatus("ACTIVE");
        consumer.setBoundDeploymentId("dep-1");
        consumer.setCreatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        consumer.setUpdatedAt(Instant.parse("2026-04-18T10:00:00Z"));

        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(entity));
        when(consumerRepository.findByConsumerIdIgnoreCase("consumer-alpha")).thenReturn(Optional.of(consumer));
        when(repository.save(any(ShopifyStoreConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(credentialService.clear("alpha.myshopify.com")).thenAnswer(invocation -> {
            entity.setDetailsJson("""
                {
                  "credentials": {
                    "status": "MISSING",
                    "checkedAt": "2026-04-18T10:05:00Z",
                    "expiring": false
                  }
                }
                """);
            return summary("alpha.myshopify.com", "MISSING", "NOT_SYNCED");
        });
        when(documentSyncService.cleanupTrackedDocuments(any(), eq("Shopify app uninstall cleanup.")))
            .thenAnswer(invocation -> {
                entity.setDetailsJson("""
                    {
                      "credentials": {
                        "status": "MISSING",
                        "checkedAt": "2026-04-18T10:05:00Z",
                        "expiring": false
                      },
                      "sync": {
                        "status": "CLEARED",
                        "checkedAt": "2026-04-18T10:06:00Z",
                        "mode": "UNINSTALL_CLEANUP",
                        "documentCount": 3,
                        "message": "Cleared 3 synced Shopify documents."
                      }
                    }
                    """);
                return new ShopifyStoreDocumentSyncService.DocumentCleanupResult("CLEARED", 3, "Cleared 3 synced Shopify documents.");
            });
        doNothing().when(platformAuditService).record(any(), any(), any(), any());

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            consumerRepository,
            platformAuditService,
            support,
            new ShopifyStoreReadinessEvaluator()
        );

        ShopifyStoreUninstallService service = new ShopifyStoreUninstallService(
            repository,
            connectionService,
            support,
            credentialService,
            documentSyncService,
            consumerRepository,
            customerConsumerService,
            platformAuditService
        );

        ShopifyStoreConnectionSummary summary = service.markUninstalled("alpha.myshopify.com", "Shopify app uninstall cleanup.");

        assertThat(summary.installStatus()).isEqualTo("UNINSTALLED");
        assertThat(summary.syncStatus()).isEqualTo("NOT_SYNCED");
        assertThat(summary.sourceReadinessStatus()).isEqualTo("NOT_RUN");
        assertThat(summary.widgetStatus()).isEqualTo("NOT_ENABLED");
        assertThat(summary.onboardingStatus()).isEqualTo("BLOCKED");
        assertThat(summary.credentials()).isNotNull();
        assertThat(summary.credentials().status()).isEqualTo("MISSING");
        assertThat(summary.syncDetail()).isNotNull();
        assertThat(summary.syncDetail().status()).isEqualTo("CLEARED");
        verify(customerConsumerService).updateBinding(eq("cust-1"), eq("consumer-alpha"), any());
        verify(customerConsumerService).updateConsumer(eq("cust-1"), eq("consumer-alpha"), any());
        verify(credentialService).clear("alpha.myshopify.com");
        verify(documentSyncService).cleanupTrackedDocuments(any(), eq("Shopify app uninstall cleanup."));
    }

    private ShopifyStoreConnectionSummary summary(String shopDomain, String credentialStatus, String syncStatus) {
        return new ShopifyStoreConnectionSummary(
            "shp-1",
            shopDomain,
            "Alpha",
            "ps-1",
            "shopify-bridge-prod",
            "Shopify Bridge",
            "cust-1",
            "Alpha Customer",
            "dep-1",
            "Alpha Deployment",
            "ACTIVE",
            "consumer-alpha",
            "Alpha Storefront",
            "INSTALLED",
            syncStatus,
            "READY",
            "ENABLED",
            "LIVE",
            true,
            true,
            true,
            true,
            false,
            false,
            new com.ai.fabric.platform.backend.shopify.model.ShopifyStoreCredentialSummary(
                credentialStatus,
                "READY".equalsIgnoreCase(credentialStatus),
                "READY".equalsIgnoreCase(credentialStatus),
                "READY".equalsIgnoreCase(credentialStatus) ? "ACCESS" : null,
                "READY".equalsIgnoreCase(credentialStatus) ? "REFRESH" : null,
                Instant.parse("2026-04-18T10:00:00Z"),
                null,
                null,
                null,
                false
            ),
            null,
            new com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSyncSummary(
                syncStatus,
                Instant.parse("2026-04-18T10:00:00Z"),
                "UNINSTALL_CLEANUP",
                0,
                "Cleanup state"
            ),
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:00:00Z")
        );
    }
}
