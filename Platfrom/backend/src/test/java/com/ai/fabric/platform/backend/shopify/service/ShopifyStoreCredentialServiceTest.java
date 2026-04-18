package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyStoreConnectionRequest;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyStoreCredentialsRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStoreCredentialServiceTest {

    @Test
    void upsertPersistsManagedSecretsAndCredentialSummary() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ShopifyStoreSourcePreflightSupport support = new ShopifyStoreSourcePreflightSupport(objectMapper);

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("ps-1");
        service.setServiceRef("shopify-bridge-prod");
        service.setDisplayName("Shopify Bridge");
        service.setProductFamily("SHOPIFY");
        service.setServiceKind("SHOPIFY_BRIDGE_SERVICE");
        when(productServiceService.requireServiceById("ps-1")).thenReturn(service);

        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-1");
        entity.setShopDomain("alpha.myshopify.com");
        entity.setDisplayName("Alpha");
        entity.setProductServiceId("ps-1");
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("NOT_SYNCED");
        entity.setSourceReadinessStatus("NOT_RUN");
        entity.setWidgetStatus("NOT_ENABLED");
        entity.setOnboardingStatus("CONNECTED");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setCreatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(entity));
        when(repository.save(any(ShopifyStoreConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        doNothing().when(platformSecretService).upsertManagedSecret(any(), any(), any());

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            consumerRepository,
            platformAuditService,
            support
        );
        ShopifyStoreCredentialService credentialService = new ShopifyStoreCredentialService(
            repository,
            connectionService,
            support,
            platformSecretService,
            platformAuditService
        );

        ShopifyStoreConnectionSummary summary = credentialService.upsert(
            "alpha.myshopify.com",
            new UpsertShopifyStoreCredentialsRequest(
                "shpat_access",
                "shprt_refresh",
                Instant.parse("2026-04-18T11:00:00Z"),
                Instant.parse("2026-07-18T10:00:00Z"),
                "read_products,read_content",
                true
            )
        );

        assertThat(summary.credentials()).isNotNull();
        assertThat(summary.credentials().status()).isEqualTo("READY");
        assertThat(summary.credentials().expiring()).isTrue();
        assertThat(summary.credentials().accessTokenPresent()).isTrue();
        assertThat(summary.credentials().refreshTokenPresent()).isTrue();
        assertThat(summary.credentials().scopesText()).isEqualTo("read_products,read_content");
        verify(platformSecretService).upsertManagedSecret(
            eq(ShopifyStoreCredentialService.accessTokenSecretRef("alpha.myshopify.com")),
            eq("shpat_access"),
            any(Map.class)
        );
        verify(platformSecretService).upsertManagedSecret(
            eq(ShopifyStoreCredentialService.refreshTokenSecretRef("alpha.myshopify.com")),
            eq("shprt_refresh"),
            any(Map.class)
        );
    }

    @Test
    void clearRemovesManagedSecretsAndMarksCredentialsMissing() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        PlatformManagedProductServiceService productServiceService = mock(PlatformManagedProductServiceService.class);
        PlatformCustomerRepository customerRepository = mock(PlatformCustomerRepository.class);
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        ShopifyStoreSourcePreflightSupport support = new ShopifyStoreSourcePreflightSupport(objectMapper);

        PlatformManagedProductServiceEntity service = new PlatformManagedProductServiceEntity();
        service.setId("ps-1");
        service.setServiceRef("shopify-bridge-prod");
        service.setDisplayName("Shopify Bridge");
        service.setProductFamily("SHOPIFY");
        service.setServiceKind("SHOPIFY_BRIDGE_SERVICE");
        when(productServiceService.requireServiceById("ps-1")).thenReturn(service);

        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-1");
        entity.setShopDomain("alpha.myshopify.com");
        entity.setDisplayName("Alpha");
        entity.setProductServiceId("ps-1");
        entity.setInstallStatus("INSTALLED");
        entity.setSyncStatus("NOT_SYNCED");
        entity.setSourceReadinessStatus("NOT_RUN");
        entity.setWidgetStatus("NOT_ENABLED");
        entity.setOnboardingStatus("CONNECTED");
        entity.setProductsEnabled(true);
        entity.setCollectionsEnabled(true);
        entity.setPagesEnabled(true);
        entity.setPoliciesEnabled(true);
        entity.setDetailsJson("""
            {
              "credentials": {
                "status": "READY",
                "checkedAt": "2026-04-18T10:00:00Z",
                "accessTokenSecretRef": "%s",
                "refreshTokenSecretRef": "%s",
                "expiring": true
              }
            }
            """.formatted(
            ShopifyStoreCredentialService.accessTokenSecretRef("alpha.myshopify.com"),
            ShopifyStoreCredentialService.refreshTokenSecretRef("alpha.myshopify.com")
        ));
        entity.setCreatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-18T10:00:00Z"));

        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(entity));
        when(repository.save(any(ShopifyStoreConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformSecretService.isManagedSecretName(any())).thenReturn(true);
        doNothing().when(platformSecretService).clearManagedSecret(any(), any());

        ShopifyStoreConnectionService connectionService = new ShopifyStoreConnectionService(
            repository,
            productServiceService,
            customerRepository,
            deploymentRepository,
            consumerRepository,
            platformAuditService,
            support
        );
        ShopifyStoreCredentialService credentialService = new ShopifyStoreCredentialService(
            repository,
            connectionService,
            support,
            platformSecretService,
            platformAuditService
        );

        ShopifyStoreConnectionSummary summary = credentialService.clear("alpha.myshopify.com");

        assertThat(summary.credentials()).isNotNull();
        assertThat(summary.credentials().status()).isEqualTo("MISSING");
        assertThat(summary.credentials().accessTokenPresent()).isFalse();
        assertThat(summary.credentials().refreshTokenPresent()).isFalse();
        verify(platformSecretService).clearManagedSecret(eq(ShopifyStoreCredentialService.accessTokenSecretRef("alpha.myshopify.com")), any(Map.class));
        verify(platformSecretService).clearManagedSecret(eq(ShopifyStoreCredentialService.refreshTokenSecretRef("alpha.myshopify.com")), any(Map.class));
    }
}
