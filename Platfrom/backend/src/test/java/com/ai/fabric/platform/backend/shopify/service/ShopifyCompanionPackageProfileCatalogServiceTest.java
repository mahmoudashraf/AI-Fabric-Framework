package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.config.ShopifyCompanionBootstrapProperties;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyCompanionPackageProfileEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyCompanionPackageProfileRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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

        ShopifyCompanionPackageProfileCatalogService service = new ShopifyCompanionPackageProfileCatalogService(
            repository,
            new ShopifyCompanionBootstrapProperties(null, null, null, null, null, null, null, null, null, null, null, null, List.of())
        );

        ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile resolved =
            service.resolve(null, "ENTERPRISE", null, null);

        assertThat(resolved.profileKey()).isEqualTo("ENTERPRISE_DEDICATED");
        assertThat(resolved.inferencePluginId()).isEqualTo("mkp-inference-dedicated-embedding-worker");
        assertThat(resolved.vectorStoragePosture()).isEqualTo("DEDICATED");
        assertThat(resolved.tierKey()).isEqualTo("ENTERPRISE");
    }

    @Test
    void fallsBackToBalancedProfileWhenSeedRowsAreUnavailable() {
        ShopifyCompanionPackageProfileRepository repository = mock(ShopifyCompanionPackageProfileRepository.class);
        when(repository.findByProfileKeyIgnoreCase("BALANCED")).thenReturn(Optional.empty());

        ShopifyCompanionPackageProfileCatalogService service = new ShopifyCompanionPackageProfileCatalogService(
            repository,
            new ShopifyCompanionBootstrapProperties(
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
                List.of("mkp-inference-shared-embeddings")
            )
        );

        ShopifyCompanionPackageProfileCatalogService.ResolvedPackageProfile resolved =
            service.resolve(null, null, null, null);

        assertThat(resolved.profileKey()).isEqualTo("BALANCED");
        assertThat(resolved.deploymentTemplateId()).isEqualTo("dev-openai-qdrant");
        assertThat(resolved.inferencePluginId()).isEqualTo("mkp-inference-shared-embeddings");
        assertThat(resolved.vectorProvisioningMode()).isEqualTo("EXTERNAL_EXISTING");
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
