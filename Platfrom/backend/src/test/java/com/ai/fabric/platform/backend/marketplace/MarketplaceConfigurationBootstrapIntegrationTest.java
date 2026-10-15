package com.ai.fabric.platform.backend.marketplace;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:configuration-only-bootstrap;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
    "platform.bootstrap.sample-enabled=false",
    "platform.bootstrap.ecommerce-demo.enabled=false"
})
@ActiveProfiles("test")
class MarketplaceConfigurationBootstrapIntegrationTest {
    @Autowired MarketplaceTemplateBootstrapService templateBootstrap;
    @Autowired DeploymentService deployments;
    @Autowired DeploymentRepository deploymentRepository;
    @Autowired VectorizationPlanRepository vectorizationPlans;

    @BeforeEach void authenticate() { PlatformTestSecurity.authenticateAsPlatformAdmin(); }
    @AfterEach void clearAuthentication() { PlatformTestSecurity.clearAuthentication(); }

    @Test
    @Sql({
        "classpath:db/migration/V148__document_knowledge_marketplace_products.sql",
        "classpath:db/migration/V149__document_knowledge_durable_vector_template.sql"
    })
    void configurationOnlyBootstrapCreatesOwnedDraftWithoutAutomaticVectorization() {
        var created = templateBootstrap.bootstrapConfiguration("mkp-template-document-knowledge-assistant",
            new CreateMarketplaceTemplateBootstrapRequest("1.1.0", "Configuration-only customer fixture",
                "staging", "dev-openai-pinecone", "PLATFORM_MANAGED", null, null));
        var draft = deployments.getActiveDraftForDeployment(created.id());
        assertThat(created.status()).isEqualTo("DRAFT");
        assertThat(created.runtimeBaseUrl()).isNull();
        assertThat(created.activeVersion()).isEqualTo("draft");
        assertThat(deploymentRepository.findById(created.id()).orElseThrow().getActiveVersionId()).isNull();
        assertThat(draft.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("PLATFORM_MANAGED");
        assertThat(vectorizationPlans.findByDeploymentId(created.id())).isEmpty();
        assertThat(deployments.listVersions(created.id())).isEmpty();
    }

}
