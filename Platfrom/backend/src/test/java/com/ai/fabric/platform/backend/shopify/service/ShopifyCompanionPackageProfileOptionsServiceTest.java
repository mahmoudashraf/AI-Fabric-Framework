package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.config.ShopifyCompanionBootstrapProperties;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDefinitionSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.service.PlatformVerificationSuiteService;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginContributionSummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePluginSummary;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceCatalogService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyCompanionPackageProfileEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyCompanionPackageProfileChoiceSummary;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyCompanionPackageProfileRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyCompanionPackageProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyCompanionPackageProfileOptionsServiceTest {

    @Test
    void optionsExposeBackendOwnedChoicesAndCompatibilityRules() {
        ShopifyCompanionPackageProfileRepository repository = mock(ShopifyCompanionPackageProfileRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        MarketplaceCatalogService marketplaceCatalogService = mock(MarketplaceCatalogService.class);
        PlatformVerificationSuiteService verificationSuiteService = mock(PlatformVerificationSuiteService.class);

        when(repository.findAllByOrderByProfileKeyAsc()).thenReturn(List.of(profile("HIGH_QUALITY", "ELITE", "ELITE")));
        when(deploymentService.listTemplates()).thenReturn(List.of(
            template("dev-openai-qdrant", "qdrant"),
            template("managed-pinecone", "pinecone")
        ));
        when(marketplaceCatalogService.listPlugins()).thenReturn(List.of(
            plugin(
                "mkp-template-shopify-companion",
                "Shopify Companion Template",
                "TEMPLATE",
                "2026.04.1",
                contributions("shopify-companion-curated", List.of())
            ),
            plugin(
                "mkp-inference-premium-hybrid",
                "Premium Hybrid Inference",
                "INFERENCE_PROFILE",
                "2026.04.2",
                contributions(null, List.of("HIGH_QUALITY"))
            )
        ));
        when(verificationSuiteService.listDefinitions()).thenReturn(List.of(
            new PlatformVerificationSuiteDefinitionSummary(
                "shopify-companion-verification",
                "Shopify Companion verification",
                "Checks Shopify Companion readiness",
                true,
                List.of()
            ),
            new PlatformVerificationSuiteDefinitionSummary(
                "ops-only",
                "Ops only",
                "Non Shopify release suite",
                true,
                List.of()
            )
        ));

        ShopifyCompanionPackageProfileOptionsService service = service(repository, deploymentService, marketplaceCatalogService, verificationSuiteService);

        var options = service.options();

        assertThat(values(options.packages())).contains("FREE", "STARTER", "ELITE", "ENTERPRISE");
        assertThat(values(options.profileKeys())).contains("LOW_COST", "BALANCED", "HIGH_QUALITY", "ENTERPRISE_DEDICATED");
        assertThat(values(options.deploymentTemplates())).contains("dev-openai-qdrant", "managed-pinecone");
        assertThat(values(options.templateVersions())).contains("2026.04.1", "2026.04.2");
        assertThat(values(options.inferencePlugins())).contains("mkp-inference-premium-hybrid", "mkp-inference-shared-embeddings");
        assertThat(values(options.verificationPacks())).contains("shopify-companion-verification", "shopify-companion-elite-readiness");
        assertThat(options.profileBlueprints()).extracting("key").contains("ELITE_HIGH_QUALITY");

        var eliteRule = options.compatibilityRules().stream()
            .filter(rule -> "ELITE".equals(rule.packageKey()) && "ELITE".equals(rule.tierKey()))
            .findFirst()
            .orElseThrow();
        assertThat(eliteRule.defaultRuntimeProfileKey()).isEqualTo("HIGH_QUALITY");
        assertThat(eliteRule.defaultVectorProfileKey()).isEqualTo("QDRANT_SHARED");
        assertThat(eliteRule.defaultInferencePluginId()).isEqualTo("mkp-inference-premium-hybrid");
        assertThat(eliteRule.allowedRuntimeProfileKeys()).contains("HIGH_QUALITY", "ENTERPRISE_DEDICATED");
        assertThat(eliteRule.allowedVectorStrategies()).contains("qdrant", "pinecone", "weaviate");
        assertThat(eliteRule.allowedDeploymentTemplateIds()).contains("dev-openai-qdrant", "managed-pinecone");
    }

    @Test
    void validateUpsertRejectsRuntimeOutsidePackageTierRule() {
        ShopifyCompanionPackageProfileRepository repository = mock(ShopifyCompanionPackageProfileRepository.class);
        when(repository.findAllByOrderByProfileKeyAsc()).thenReturn(List.of());
        ShopifyCompanionPackageProfileOptionsService service = service(
            repository,
            mock(DeploymentService.class),
            mock(MarketplaceCatalogService.class),
            mock(PlatformVerificationSuiteService.class)
        );

        assertThatThrownBy(() -> service.validateUpsert("LOW_COST", request(
            "FREE",
            "FREE",
            "HIGH_QUALITY",
            "QDRANT_SHARED",
            "LOW",
            "mkp-inference-shared-embeddings",
            "starter-launch-readiness"
        ))).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("runtimeProfileKey HIGH_QUALITY is not approved for package/tier FREE/FREE");
    }

    @Test
    void validateUpsertAcceptsEliteHighQualityApprovedCombination() {
        ShopifyCompanionPackageProfileRepository repository = mock(ShopifyCompanionPackageProfileRepository.class);
        when(repository.findAllByOrderByProfileKeyAsc()).thenReturn(List.of());
        ShopifyCompanionPackageProfileOptionsService service = service(
            repository,
            mock(DeploymentService.class),
            mock(MarketplaceCatalogService.class),
            mock(PlatformVerificationSuiteService.class)
        );

        assertThatCode(() -> service.validateUpsert("HIGH_QUALITY", request(
            "ELITE",
            "ELITE",
            "HIGH_QUALITY",
            "QDRANT_SHARED",
            "QUALITY",
            "mkp-inference-premium-hybrid",
            "shopify-companion-elite-readiness"
        ))).doesNotThrowAnyException();
    }

    private ShopifyCompanionPackageProfileOptionsService service(ShopifyCompanionPackageProfileRepository repository,
                                                                 DeploymentService deploymentService,
                                                                 MarketplaceCatalogService marketplaceCatalogService,
                                                                 PlatformVerificationSuiteService verificationSuiteService) {
        return new ShopifyCompanionPackageProfileOptionsService(
            repository,
            bootstrapProperties(),
            deploymentService,
            marketplaceCatalogService,
            verificationSuiteService
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

    private UpsertShopifyCompanionPackageProfileRequest request(String packageKey,
                                                                String tierKey,
                                                                String runtimeProfileKey,
                                                                String vectorProfileKey,
                                                                String costPosture,
                                                                String inferencePluginId,
                                                                String verificationPackId) {
        return new UpsertShopifyCompanionPackageProfileRequest(
            packageKey,
            tierKey,
            runtimeProfileKey,
            vectorProfileKey,
            runtimeProfileKey,
            "Test profile",
            costPosture,
            "mkp-template-shopify-companion",
            "",
            "dev-openai-qdrant",
            inferencePluginId,
            "qdrant",
            "EXTERNAL_EXISTING",
            "SHARED",
            verificationPackId,
            "DRAFT",
            "{}",
            "test"
        );
    }

    private DeploymentTemplateSummary template(String id, String vectorStrategy) {
        return new DeploymentTemplateSummary(
            id,
            id,
            id + " template",
            "openai",
            "openai",
            vectorStrategy,
            "runtime-managed",
            "connector-hosted",
            "qdrant".equals(vectorStrategy),
            "qdrant".equals(vectorStrategy) ? "PLATFORM_MANAGED" : "EXTERNAL_EXISTING",
            vectorStrategy + " summary"
        );
    }

    private MarketplacePluginSummary plugin(String id,
                                            String displayName,
                                            String pluginType,
                                            String latestVersion,
                                            MarketplacePluginContributionSummary contributions) {
        return new MarketplacePluginSummary(
            id,
            id,
            displayName,
            pluginType,
            "loom",
            "LoomAI",
            displayName + " plugin",
            "ACTIVE",
            latestVersion,
            null,
            List.of("shopify"),
            contributions,
            Instant.parse("2026-04-29T00:00:00Z")
        );
    }

    private MarketplacePluginContributionSummary contributions(String templateCuratedModuleId,
                                                               List<String> inferenceProfileIds) {
        return new MarketplacePluginContributionSummary(
            templateCuratedModuleId,
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            inferenceProfileIds,
            List.of(),
            List.of()
        );
    }

    private ShopifyCompanionPackageProfileEntity profile(String profileKey,
                                                         String packageKey,
                                                         String tierKey) {
        ShopifyCompanionPackageProfileEntity entity = new ShopifyCompanionPackageProfileEntity();
        entity.setId("scp-" + profileKey.toLowerCase());
        entity.setProfileKey(profileKey);
        entity.setPackageKey(packageKey);
        entity.setTierKey(tierKey);
        entity.setRuntimeProfileKey(profileKey);
        entity.setVectorProfileKey("QDRANT_SHARED");
        entity.setDisplayName(profileKey);
        entity.setDescription(profileKey);
        entity.setCostPosture("QUALITY");
        entity.setTemplatePluginId("mkp-template-shopify-companion");
        entity.setTemplatePluginVersion("");
        entity.setDeploymentTemplateId("dev-openai-qdrant");
        entity.setInferencePluginId("mkp-inference-premium-hybrid");
        entity.setVectorStrategy("qdrant");
        entity.setVectorProvisioningMode("EXTERNAL_EXISTING");
        entity.setVectorStoragePosture("SHARED");
        entity.setVerificationPackId("shopify-companion-elite-readiness");
        entity.setStatus("ACTIVE");
        entity.setDetailsJson("{}");
        entity.setCreatedAt(Instant.parse("2026-04-29T00:00:00Z"));
        entity.setUpdatedAt(Instant.parse("2026-04-29T00:00:00Z"));
        return entity;
    }

    private List<String> values(List<ShopifyCompanionPackageProfileChoiceSummary> choices) {
        return choices.stream().map(ShopifyCompanionPackageProfileChoiceSummary::value).toList();
    }
}
