package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "platform.bootstrap.sample-enabled=false",
    "platform.bootstrap.ecommerce-demo.enabled=true",
    "platform.bootstrap.ecommerce-demo.auto-apply=false"
})
@ActiveProfiles("test")
class EcommerceDemoBootstrapServiceIntegrationTest {

    @Autowired
    private EcommerceDemoBootstrapService ecommerceDemoBootstrapService;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private DeploymentService deploymentService;

    @Test
    void bootstrapCreatesPublishedDemoOnlyOnce() {
        ecommerceDemoBootstrapService.ensureBootstrapDeployment();
        ecommerceDemoBootstrapService.ensureBootstrapDeployment();

        var deployment = deploymentRepository
            .findByNameIgnoreCaseAndEnvironmentNameIgnoreCaseAndArchivedAtIsNull("Ecommerce Demo Restored", "dev")
            .orElseThrow();

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.getId());
        DeploymentVersionSummary latestVersion = deploymentService.latestVersion(deployment.getId());

        assertThat(draft.revisionNumber()).isEqualTo(2);
        assertThat(latestVersion.versionLabel()).isEqualTo("v1");
        assertThat(deploymentService.listVersions(deployment.getId())).hasSize(1);
        assertThat(draft.actionsConfig().path("actions")).isNotEmpty();
        assertThat(draft.entityConfig().path("ai-entities").fieldNames().hasNext()).isTrue();
        assertThat(draft.routingConfig().path("actions").fieldNames().hasNext()).isTrue();
    }
}
