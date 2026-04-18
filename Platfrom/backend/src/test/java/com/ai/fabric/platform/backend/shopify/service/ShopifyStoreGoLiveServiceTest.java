package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreReadinessSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStoreGoLiveServiceTest {

    @Test
    void goLivePublishesAndAppliesDeployment() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity store = store("READY");
        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store));
        when(repository.save(any(ShopifyStoreConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentService.getActiveDraftForDeployment("dep-1")).thenReturn(new DeploymentDraftResponse(
            "drf-1",
            "dep-1",
            3,
            "DRAFT",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:00:00Z")
        ));
        when(deploymentService.publishDraft("drf-1")).thenReturn(new DeploymentVersionSummary(
            "ver-1",
            "dep-1",
            "drf-1",
            "v4",
            "PUBLISHED",
            "hash-1",
            false,
            Instant.parse("2026-04-18T10:05:00Z")
        ));
        when(deploymentService.applyVersion("dep-1", "ver-1")).thenReturn(new DeploymentReleaseSummary(
            "rel-1",
            "dep-1",
            "ver-1",
            "APPLY_REQUESTED",
            "PENDING",
            "QUEUED",
            "RAILWAY",
            "queue_release",
            "Apply request accepted and queued.",
            null,
            null,
            null,
            Instant.parse("2026-04-18T10:05:10Z"),
            Instant.parse("2026-04-18T10:05:10Z"),
            Instant.parse("2026-04-18T10:05:10Z")
        ));
        when(connectionService.getConnection("alpha.myshopify.com")).thenReturn(summary("alpha.myshopify.com", "GO_LIVE_REQUESTED"));

        ShopifyStoreGoLiveService service = new ShopifyStoreGoLiveService(
            repository,
            deploymentService,
            connectionService,
            auditService
        );

        ShopifyStoreConnectionSummary result = service.goLive("alpha.myshopify.com");

        assertThat(result.onboardingStatus()).isEqualTo("GO_LIVE_REQUESTED");
        assertThat(store.getOnboardingStatus()).isEqualTo("GO_LIVE_REQUESTED");
        verify(deploymentService).publishDraft("drf-1");
        verify(deploymentService).applyVersion("dep-1", "ver-1");
    }

    @Test
    void goLiveRejectsWhenStoreIsNotPreflightReady() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store("BLOCKED")));
        when(connectionService.getConnection("alpha.myshopify.com")).thenReturn(summary(
            "alpha.myshopify.com",
            "PREFLIGHT_READY",
            new ShopifyStoreReadinessSummary(
                "BLOCKED",
                false,
                false,
                java.util.List.of("Shopify source readiness is not READY yet."),
                java.util.List.of("Shopify source readiness is not READY yet."),
                java.util.List.of("Run source preflight and resolve any blocked Shopify source categories.")
            )
        ));

        ShopifyStoreGoLiveService service = new ShopifyStoreGoLiveService(
            repository,
            deploymentService,
            connectionService,
            auditService
        );

        assertThatThrownBy(() -> service.goLive("alpha.myshopify.com"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("source readiness is not READY");
    }

    private ShopifyStoreConnectionEntity store(String sourceReadinessStatus) {
        ShopifyStoreConnectionEntity entity = new ShopifyStoreConnectionEntity();
        entity.setId("shp-1");
        entity.setShopDomain("alpha.myshopify.com");
        entity.setInstallStatus("INSTALLED");
        entity.setSourceReadinessStatus(sourceReadinessStatus);
        entity.setOnboardingStatus("PREFLIGHT_READY");
        entity.setCustomerId("cust-1");
        entity.setDeploymentId("dep-1");
        entity.setConsumerId("consumer-alpha");
        entity.setCreatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        return entity;
    }

    private ShopifyStoreConnectionSummary summary(String shopDomain, String onboardingStatus) {
        return summary(
            shopDomain,
            onboardingStatus,
            new ShopifyStoreReadinessSummary(
                "READY_FOR_GO_LIVE",
                true,
                false,
                java.util.List.of(),
                java.util.List.of("No verified deployment release exists yet."),
                java.util.List.of("Request go-live to publish, apply, and verify the deployment.")
            )
        );
    }

    private ShopifyStoreConnectionSummary summary(String shopDomain,
                                                  String onboardingStatus,
                                                  ShopifyStoreReadinessSummary readiness) {
        return new ShopifyStoreConnectionSummary(
            "shp-1",
            shopDomain,
            "Alpha",
            "ps-1",
            "shopify-bridge-prod",
            "Shopify Bridge Prod",
            "cust-1",
            "Alpha Customer",
            "dep-1",
            "Alpha Deployment",
            "APPLY_REQUESTED",
            "consumer-alpha",
            "Alpha Storefront",
            "INSTALLED",
            "NOT_SYNCED",
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
            readiness,
            null,
            null,
            Instant.parse("2026-04-18T10:00:00Z"),
            null,
            null,
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:00:00Z")
        );
    }
}
