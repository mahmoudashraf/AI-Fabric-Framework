package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.ShopifyCompanionBootstrapProperties;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyCompanionPackageProfileEntity;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyCompanionPackageProfileStatusRequest;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyCompanionPackageProfileRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyCompanionPackageProfileRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyCompanionPackageProfileCatalogServiceTest {

    @Test
    void resolvesEnterpriseTierToDedicatedRuntimeProfile() {
        ShopifyCompanionPackageProfileRepository repository = mock(ShopifyCompanionPackageProfileRepository.class);
        ShopifyCompanionPackageProfileEntity enterprise = profile(
            "scp-enterprise",
            "ENTERPRISE_DEDICATED",
            "ENTERPRISE",
            "ENTERPRISE",
            "ENTERPRISE_DEDICATED",
            "QDRANT_DEDICATED",
            "mkp-inference-dedicated-embedding-worker",
            "PLATFORM_MANAGED",
            "DEDICATED"
        );
        when(repository.findFirstByTierKeyIgnoreCaseAndStatusIgnoreCaseOrderByUpdatedAtDesc("ENTERPRISE", "ACTIVE"))
            .thenReturn(Optional.of(enterprise));

        ShopifyCompanionPackageProfileCatalogService service = service(repository, mock(PlatformAuditService.class));

        ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile resolved =
            service.resolve(null, "ENTERPRISE", null, null);

        assertThat(resolved.profileKey()).isEqualTo("ENTERPRISE_DEDICATED");
        assertThat(resolved.inferencePluginId()).isEqualTo("mkp-inference-dedicated-embedding-worker");
        assertThat(resolved.vectorStoragePosture()).isEqualTo("DEDICATED");
        assertThat(resolved.tierKey()).isEqualTo("ENTERPRISE");
    }

    @Test
    void resolvesRequiredAndDisabledPluginBundlesFromProfileDetails() {
        ShopifyCompanionPackageProfileRepository repository = mock(ShopifyCompanionPackageProfileRepository.class);
        ShopifyCompanionPackageProfileEntity elite = profile(
            "scp-high",
            "HIGH_QUALITY",
            "ELITE",
            "ELITE",
            "HIGH_QUALITY",
            "QDRANT_SHARED",
            "mkp-inference-premium-hybrid",
            "EXTERNAL_EXISTING",
            "SHARED"
        );
        elite.setDetailsJson("""
            {
              "requiredPluginIds": [
                "mkp-action-shopify-storefront-read-mcp",
                "mkp-action-shopify-cart-mcp",
                "mkp-inference-premium-hybrid"
              ],
              "disabledPluginIds": [
                "mkp-action-shopify-companion-read",
                "mkp-action-shopify-customer-account-mcp"
              ]
            }
            """);
        when(repository.findByProfileKeyIgnoreCase("HIGH_QUALITY")).thenReturn(Optional.of(elite));

        ShopifyCompanionPackageProfileCatalogService service = service(repository, mock(PlatformAuditService.class));

        ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile resolved =
            service.resolve(null, null, "HIGH_QUALITY", null);

        assertThat(resolved.requiredPluginIds()).containsExactly(
            "mkp-action-shopify-storefront-read-mcp",
            "mkp-action-shopify-cart-mcp",
            "mkp-inference-premium-hybrid"
        );
        assertThat(resolved.disabledPluginIds()).contains(
            "mkp-action-shopify-companion-read",
            "mkp-action-shopify-customer-account-mcp",
            "mkp-action-shopify-checkout-mcp"
        );
        assertThat(resolved.disabledPluginIds()).doesNotContain("mkp-action-shopify-storefront-read-mcp");
        assertThat(resolved.disabledPluginIds()).doesNotContain("mkp-action-shopify-cart-mcp");
    }

    @Test
    void fallsBackToHighQualityProfileWhenSeedRowsAreUnavailable() {
        ShopifyCompanionPackageProfileRepository repository = mock(ShopifyCompanionPackageProfileRepository.class);
        when(repository.findByProfileKeyIgnoreCase("HIGH_QUALITY")).thenReturn(Optional.empty());

        ShopifyCompanionPackageProfileCatalogService service = service(repository, mock(PlatformAuditService.class));

        ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile resolved =
            service.resolve(null, null, null, null);

        assertThat(resolved.profileKey()).isEqualTo("HIGH_QUALITY");
        assertThat(resolved.deploymentTemplateId()).isEqualTo("dev-openai-qdrant");
        assertThat(resolved.inferencePluginId()).isEqualTo("mkp-inference-premium-hybrid");
        assertThat(resolved.vectorProvisioningMode()).isEqualTo("EXTERNAL_EXISTING");
        assertThat(resolved.requiredPluginIds()).contains("mkp-action-shopify-storefront-read-mcp");
        assertThat(resolved.requiredPluginIds()).contains("mkp-action-shopify-cart-mcp");
        assertThat(resolved.requiredPluginIds()).contains("mkp-action-shopify-customer-account-mcp");
        assertThat(resolved.requiredPluginIds()).contains("mkp-action-shopify-checkout-mcp");
    }

    @Test
    void upsertsValidatedProfileAndAuditsTheCatalogWrite() {
        ShopifyCompanionPackageProfileRepository repository = mock(ShopifyCompanionPackageProfileRepository.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        ShopifyCompanionPackageProfileEntity existing = profile(
            "scp-high",
            "HIGH_QUALITY",
            "ELITE",
            "ELITE",
            "HIGH_QUALITY",
            "QDRANT_SHARED",
            "mkp-inference-premium-hybrid",
            "EXTERNAL_EXISTING",
            "SHARED"
        );
        when(repository.findByProfileKeyIgnoreCase("HIGH_QUALITY")).thenReturn(Optional.of(existing));
        when(repository.findAllByPackageKeyIgnoreCaseAndTierKeyIgnoreCaseAndStatusIgnoreCaseOrderByUpdatedAtDesc("ELITE", "ELITE", "ACTIVE"))
            .thenReturn(List.of(existing));
        when(repository.save(any(ShopifyCompanionPackageProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShopifyCompanionPackageProfileCatalogService service = service(repository, auditService);

        var result = service.upsertProfile("high_quality", new UpsertShopifyCompanionPackageProfileRequest(
            "elite",
            "elite",
            "high_quality",
            "qdrant_shared",
            "High quality",
            "Premium shared profile",
            "quality",
            "mkp-template-shopify-companion",
            "",
            "dev-openai-qdrant",
            "mkp-inference-premium-hybrid",
            "QDRANT",
            "external_existing",
            "shared",
            "shopify-companion-elite-readiness",
            "active",
            "{\"notes\":\"production mapping\"}",
            "Tune elite profile"
        ));

        assertThat(result.profileKey()).isEqualTo("HIGH_QUALITY");
        assertThat(result.packageKey()).isEqualTo("ELITE");
        assertThat(result.runtimeProfileKey()).isEqualTo("HIGH_QUALITY");
        assertThat(result.vectorProfileKey()).isEqualTo("QDRANT_SHARED");
        assertThat(result.templatePluginId()).isEqualTo("mkp-template-shopify-companion");
        assertThat(result.inferencePluginId()).isEqualTo("mkp-inference-premium-hybrid");
        assertThat(result.vectorStrategy()).isEqualTo("qdrant");
        assertThat(result.vectorProvisioningMode()).isEqualTo("EXTERNAL_EXISTING");
        assertThat(result.vectorStoragePosture()).isEqualTo("SHARED");
        assertThat(result.detailsJson()).contains("\"notes\":\"production mapping\"");
        verify(auditService).record(
            eq("SHOPIFY_COMPANION_PACKAGE_PROFILE_UPSERTED"),
            eq("SHOPIFY_COMPANION_PACKAGE_PROFILE"),
            eq("HIGH_QUALITY"),
            anyMap()
        );
    }

    @Test
    void rejectsDuplicateActivePackageTierMappings() {
        ShopifyCompanionPackageProfileRepository repository = mock(ShopifyCompanionPackageProfileRepository.class);
        ShopifyCompanionPackageProfileEntity existing = profile(
            "scp-balanced",
            "BALANCED",
            "STARTER",
            "STARTER",
            "BALANCED",
            "QDRANT_SHARED",
            "mkp-inference-shared-embeddings",
            "EXTERNAL_EXISTING",
            "SHARED"
        );
        when(repository.findAllByPackageKeyIgnoreCaseAndTierKeyIgnoreCaseAndStatusIgnoreCaseOrderByUpdatedAtDesc("STARTER", "STARTER", "ACTIVE"))
            .thenReturn(List.of(existing));

        ShopifyCompanionPackageProfileCatalogService service = service(repository, mock(PlatformAuditService.class));

        assertThatThrownBy(() -> service.upsertProfile("BALANCED_V2", new UpsertShopifyCompanionPackageProfileRequest(
            "STARTER",
            "STARTER",
            "BALANCED_V2",
            "QDRANT_SHARED",
            "Balanced v2",
            "New starter profile",
            "STANDARD",
            "mkp-template-shopify-companion",
            null,
            "dev-openai-qdrant",
            "mkp-inference-shared-embeddings",
            "qdrant",
            "EXTERNAL_EXISTING",
            "SHARED",
            "starter-launch-readiness",
            "ACTIVE",
            "{}",
            "activate replacement"
        ))).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void rejectsMalformedProfileDetailsJson() {
        ShopifyCompanionPackageProfileRepository repository = mock(ShopifyCompanionPackageProfileRepository.class);
        ShopifyCompanionPackageProfileCatalogService service = service(repository, mock(PlatformAuditService.class));

        assertThatThrownBy(() -> service.upsertProfile("BROKEN_JSON", new UpsertShopifyCompanionPackageProfileRequest(
            "STARTER",
            "STARTER",
            "BROKEN_JSON",
            "QDRANT_SHARED",
            "Broken JSON",
            "Invalid details",
            "STANDARD",
            "mkp-template-shopify-companion",
            null,
            "dev-openai-qdrant",
            "mkp-inference-shared-embeddings",
            "qdrant",
            "EXTERNAL_EXISTING",
            "SHARED",
            "starter-launch-readiness",
            "DRAFT",
            "[\"not-object\"]",
            "validate"
        ))).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("detailsJson must be a JSON object");
    }

    @Test
    void updatesProfileStatusAndAuditsTheChange() {
        ShopifyCompanionPackageProfileRepository repository = mock(ShopifyCompanionPackageProfileRepository.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        ShopifyCompanionPackageProfileEntity existing = profile(
            "scp-high",
            "HIGH_QUALITY",
            "ELITE",
            "ELITE",
            "HIGH_QUALITY",
            "QDRANT_SHARED",
            "mkp-inference-premium-hybrid",
            "EXTERNAL_EXISTING",
            "SHARED"
        );
        when(repository.findByProfileKeyIgnoreCase("HIGH_QUALITY")).thenReturn(Optional.of(existing));
        when(repository.save(any(ShopifyCompanionPackageProfileEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShopifyCompanionPackageProfileCatalogService service = service(repository, auditService);

        var result = service.updateProfileStatus(
            "HIGH_QUALITY",
            new UpdateShopifyCompanionPackageProfileStatusRequest("disabled", "Retire profile")
        );

        assertThat(result.status()).isEqualTo("DISABLED");
        verify(auditService).record(
            eq("SHOPIFY_COMPANION_PACKAGE_PROFILE_STATUS_CHANGED"),
            eq("SHOPIFY_COMPANION_PACKAGE_PROFILE"),
            eq("HIGH_QUALITY"),
            any(Map.class)
        );
    }

    private ShopifyCompanionPackageProfileCatalogService service(ShopifyCompanionPackageProfileRepository repository,
                                                                 PlatformAuditService auditService) {
        return new ShopifyCompanionPackageProfileCatalogService(
            repository,
            bootstrapProperties(),
            auditService,
            new ObjectMapper(),
            mock(ShopifyCompanionPackageProfileOptionsService.class)
        );
    }

    private ShopifyCompanionBootstrapProperties bootstrapProperties() {
        return new ShopifyCompanionBootstrapProperties(
            "dev",
            "dev-openai-qdrant",
            "EXTERNAL_EXISTING",
            "SHARED",
            null,
            null,
            null,
            null,
            null,
            false,
            "mkp-template-shopify-companion",
            "",
            "mkp-template-shopify-companion-staging",
            "mkp-template-shopify-companion-production",
            "",
            List.of("mkp-inference-shared-embeddings")
        );
    }

    private ShopifyCompanionPackageProfileEntity profile(String id,
                                                         String profileKey,
                                                         String packageKey,
                                                         String tierKey,
                                                         String runtimeProfileKey,
                                                         String vectorProfileKey,
                                                         String inferencePluginId,
                                                         String vectorProvisioningMode,
                                                         String vectorStoragePosture) {
        ShopifyCompanionPackageProfileEntity entity = new ShopifyCompanionPackageProfileEntity();
        Instant now = Instant.parse("2026-04-28T00:00:00Z");
        entity.setId(id);
        entity.setProfileKey(profileKey);
        entity.setPackageKey(packageKey);
        entity.setTierKey(tierKey);
        entity.setRuntimeProfileKey(runtimeProfileKey);
        entity.setVectorProfileKey(vectorProfileKey);
        entity.setDisplayName(profileKey);
        entity.setDescription(profileKey);
        entity.setCostPosture("STANDARD");
        entity.setTemplatePluginId("mkp-template-shopify-companion");
        entity.setTemplatePluginVersion(null);
        entity.setDeploymentTemplateId("dev-openai-qdrant");
        entity.setInferencePluginId(inferencePluginId);
        entity.setVectorStrategy("qdrant");
        entity.setVectorProvisioningMode(vectorProvisioningMode);
        entity.setVectorStoragePosture(vectorStoragePosture);
        entity.setVerificationPackId("starter-launch-readiness");
        entity.setStatus("ACTIVE");
        entity.setDetailsJson("{}");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }
}
