package com.ai.fabric.platform.backend.marketplace;

import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
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

    @BeforeEach void authenticate() { PlatformTestSecurity.authenticateAsPlatformAdmin(); }
    @AfterEach void clearAuthentication() { PlatformTestSecurity.clearAuthentication(); }

    @Test
    void dealershipConfigurationBootstrapPreservesReviewedManagedHandoverBinding() {
        var created = templateBootstrap.bootstrapConfiguration("mkp-template-simulated-dealership-experience-v1",
            new CreateMarketplaceTemplateBootstrapRequest("1.2.0", "Dealership customer configuration fixture",
                "staging", "custom-start-from-scratch", null, null, null));

        var draft = deployments.getActiveDraftForDeployment(created.id());
        var handover = draft.behaviorConfig().path("conversationHandover");
        assertThat(handover.path("channelBindingId").asText()).isEqualTo("${MANAGED_INBOX_BINDING_ID}");
        assertThat(handover.path("actionId").asText()).isEqualTo("request_conversation_handover");
        assertThat(handover.path("actionBinding").path("implementation").asText())
            .isEqualTo("SERVER_OWNED_RUNTIME_ACTION");
        assertThat(handover.path("channel").path("bindingId").asText())
            .isEqualTo("${MANAGED_INBOX_BINDING_ID}");
        assertThat(deployments.listVersions(created.id())).isEmpty();
    }
}
