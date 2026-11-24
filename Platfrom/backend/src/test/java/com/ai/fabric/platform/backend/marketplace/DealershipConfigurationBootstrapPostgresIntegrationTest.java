package com.ai.fabric.platform.backend.marketplace;

import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.flyway.locations=classpath:db/migration",
    "spring.flyway.target=latest",
    "platform.bootstrap.sample-enabled=false",
    "platform.bootstrap.ecommerce-demo.enabled=false"
})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class DealershipConfigurationBootstrapPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired MarketplaceTemplateBootstrapService templateBootstrap;
    @Autowired DeploymentService deployments;
    @Autowired DeploymentRepository deploymentRepository;
    @Autowired DeploymentMarketplacePluginInstallRepository installRepository;
    @Autowired DeploymentMarketplaceInstallService installService;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach void authenticate() { PlatformTestSecurity.authenticateAsPlatformAdmin(); }
    @AfterEach void clearAuthentication() { PlatformTestSecurity.clearAuthentication(); }

    @Test
    void dealershipConfigurationBootstrapUsesDurableVectorBaselineAndEnablesDocumentKnowledge() {
        var created = templateBootstrap.bootstrapConfiguration("mkp-template-simulated-dealership-experience-v1",
            new CreateMarketplaceTemplateBootstrapRequest("1.10.0", "Dealership customer configuration fixture",
                "staging", null, null, null, null));

        var draft = deployments.getActiveDraftForDeployment(created.id());
        assertThat(draft.providerConfig().path("vectorStrategy").asText()).isEqualTo("milvus");
        assertThat(draft.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("EXTERNAL_EXISTING");
        assertThat(draft.providerConfig().path("vectorStoragePosture").asText()).isEqualTo("SHARED");
        assertThat(draft.providerConfig().path("milvusExternalBindingRequired").asBoolean()).isTrue();
        assertThat(draft.providerConfig().path("milvusSecure").asBoolean()).isTrue();
        assertThat(draft.behaviorConfig().path("executionExtensions"))
            .noneSatisfy(extension -> assertThat(extension.asText()).isEqualTo("CONVERSATION_HANDOVER"));
        assertThat(installRepository.findByDeploymentIdAndPluginId(created.id(),
            "mkp-integration-basic-loomai-inbox")).isEmpty();

        var deployment = deploymentRepository.findById(created.id()).orElseThrow();
        var knowledge = installRepository.findByDeploymentIdAndPluginId(
            created.id(),
            "mkp-data-dealership-knowledge-v1"
        ).orElseThrow();
        var knowledgeConfig = objectMapper.createObjectNode();
        knowledgeConfig.put("documentStorageBindingRef", "slot:document-storage");
        knowledgeConfig.put("publicContentApprovalConfirmed", true);
        installService.updateInstallForTrustedCaller(
            deployment,
            knowledge.getId(),
            new UpdateDeploymentMarketplaceInstallRequest(
                null,
                "ENABLED",
                knowledgeConfig,
                objectMapper.createObjectNode()
            )
        );

        var compiled = deployments.getActiveDraftForDeployment(created.id());
        assertThat(compiled.marketplaceDatasetConfig().path("datasets"))
            .anySatisfy(dataset -> {
                assertThat(dataset.path("entityType").asText()).isEqualTo("document");
                assertThat(dataset.path("sourceConnector").path("bindingRef").asText())
                    .isEqualTo("slot:document-storage");
            });
        assertThat(deployments.listVersions(created.id())).isEmpty();
    }
}
