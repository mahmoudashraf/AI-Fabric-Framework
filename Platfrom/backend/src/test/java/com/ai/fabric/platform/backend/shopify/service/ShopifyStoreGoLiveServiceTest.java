package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentReleaseSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreReadinessSummary;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStoreGoLiveServiceTest {

    @Test
    void goLivePublishesAndAppliesDeployment() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity store = store("READY");
        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store));
        when(repository.save(any(ShopifyStoreConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productServiceRepository.findById("ps-1")).thenReturn(Optional.of(productService("ps-1")));
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
        when(deploymentService.updateDraft(eq("drf-1"), any(UpdateDeploymentDraftRequest.class))).thenReturn(new DeploymentDraftResponse(
            "drf-1",
            "dep-1",
            3,
            "MODIFIED",
            null,
            null,
            null,
            null,
            JsonNodeFactory.instance.objectNode().put("authzMode", "ALLOW_VERIFIED"),
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:01:00Z")
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
            productServiceRepository,
            connectionService,
            auditService
        );

        ShopifyStoreConnectionSummary result = service.goLive("alpha.myshopify.com");

        assertThat(result.onboardingStatus()).isEqualTo("GO_LIVE_REQUESTED");
        assertThat(store.getOnboardingStatus()).isEqualTo("GO_LIVE_REQUESTED");
        verify(deploymentService).updateDraft(eq("drf-1"), argThat(request ->
            matchesShopifyCompanionSecurityDefaults(request)
        ));
        verify(deploymentService).updateDraft(eq("drf-1"), argThat(this::matchesShopifyBridgeRoutingDefaults));
        verify(deploymentService).publishDraft("drf-1");
        verify(deploymentService).applyVersion("dep-1", "ver-1");
    }

    @Test
    void goLiveRepairsMissingBridgePrivateRuntimeIssuerEvenWhenAllowVerifiedAlreadySet() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
        ShopifyStoreConnectionService connectionService = mock(ShopifyStoreConnectionService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);

        ShopifyStoreConnectionEntity store = store("READY");
        when(repository.findByShopDomainIgnoreCase("alpha.myshopify.com")).thenReturn(Optional.of(store));
        when(repository.save(any(ShopifyStoreConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productServiceRepository.findById("ps-1")).thenReturn(Optional.of(productService("ps-1")));

        ObjectNode securityConfig = JsonNodeFactory.instance.objectNode();
        securityConfig.put("authzMode", "ALLOW_VERIFIED");
        when(deploymentService.getActiveDraftForDeployment("dep-1")).thenReturn(new DeploymentDraftResponse(
            "drf-1",
            "dep-1",
            3,
            "DRAFT",
            null,
            null,
            null,
            null,
            securityConfig,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:00:00Z")
        ));
        when(deploymentService.updateDraft(eq("drf-1"), any(UpdateDeploymentDraftRequest.class))).thenReturn(new DeploymentDraftResponse(
            "drf-1",
            "dep-1",
            3,
            "MODIFIED",
            null,
            null,
            null,
            null,
            securityConfig,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-18T10:00:00Z"),
            Instant.parse("2026-04-18T10:01:00Z")
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
            productServiceRepository,
            connectionService,
            auditService
        );

        service.goLive("alpha.myshopify.com");

        verify(deploymentService).updateDraft(eq("drf-1"), argThat(this::matchesShopifyCompanionSecurityDefaults));
    }

    @Test
    void goLiveRejectsWhenStoreIsNotPreflightReady() {
        ShopifyStoreConnectionRepository repository = mock(ShopifyStoreConnectionRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        PlatformManagedProductServiceRepository productServiceRepository = mock(PlatformManagedProductServiceRepository.class);
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
            productServiceRepository,
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
        entity.setProductServiceId("ps-1");
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

    private boolean matchesShopifyBridgeRoutingDefaults(UpdateDeploymentDraftRequest request) {
        if (request == null || !(request.routingConfig() instanceof ObjectNode routing)) {
            return false;
        }
        ObjectNode upstream = (ObjectNode) routing.path("connector").path("upstream");
        ObjectNode auth = (ObjectNode) upstream.path("auth");
        ObjectNode listProducts = (ObjectNode) routing.path("actions").path("list_products");
        ObjectNode requestBody = (ObjectNode) listProducts.path("request").path("body");
        return "https://shopify-bridge.example.com".equals(upstream.path("base-url").asText())
            && "API_KEY".equals(auth.path("type").asText())
            && "X-BRIDGE-API-KEY".equals(auth.path("header").asText())
            && "${SHOPIFY_BRIDGE_SHARED_SECRET}".equals(auth.path("value").asText())
            && "POST".equals(listProducts.path("method").asText())
            && "/api/admin/stores/alpha.myshopify.com/actions/execute".equals(listProducts.path("path").asText())
            && "{{actionId}}".equals(requestBody.path("actionId").asText())
            && "{{params}}".equals(requestBody.path("params").asText())
            && "{{idempotencyKey}}".equals(requestBody.path("idempotencyKey").asText())
            && "{{trace}}".equals(requestBody.path("trace").asText());
    }

    private boolean matchesShopifyCompanionSecurityDefaults(UpdateDeploymentDraftRequest request) {
        if (request == null || !(request.securityConfig() instanceof ObjectNode security)) {
            return false;
        }
        String issuers = security.path("privateRuntimeAcceptedIssuers").asText("");
        String audiences = security.path("privateRuntimeAcceptedAudiences").asText("");
        return "ALLOW_VERIFIED".equals(security.path("authzMode").asText())
            && issuers.contains("platform-consumer-bridge")
            && issuers.contains("platform-poc:SESSION")
            && audiences.contains("dep-1");
    }

    private PlatformManagedProductServiceEntity productService(String id) {
        PlatformManagedProductServiceEntity entity = new PlatformManagedProductServiceEntity();
        entity.setId(id);
        entity.setBaseUrl("https://shopify-bridge.example.com/");
        entity.setServiceKind("SHOPIFY_BRIDGE_SERVICE");
        entity.setServiceRef("shopify-bridge-prod");
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-18T10:00:00Z"));
        return entity;
    }
}
