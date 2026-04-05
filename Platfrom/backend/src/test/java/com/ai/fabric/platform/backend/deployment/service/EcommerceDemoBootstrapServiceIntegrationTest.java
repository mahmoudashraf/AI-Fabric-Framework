package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private VectorizationSourceConnectionRepository vectorizationSourceConnectionRepository;

    @Autowired
    private VectorizationPlanRepository vectorizationPlanRepository;

    @Autowired
    private VectorizationPlanRevisionRepository vectorizationPlanRevisionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void bootstrapCreatesPublishedDemoOnlyOnce() throws Exception {
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

        var connection = vectorizationSourceConnectionRepository.findByDeploymentId(deployment.getId()).orElseThrow();
        var plan = vectorizationPlanRepository.findByDeploymentId(deployment.getId()).orElseThrow();
        var revision = vectorizationPlanRevisionRepository.findById(plan.getActiveRevisionId()).orElseThrow();

        assertThat(connection.getAdapterType()).isEqualTo("REST_API");
        assertThat(connection.getAuthMode()).isEqualTo("NONE");
        assertThat(connection.getStatus()).isEqualTo("READY");
        assertThat(objectMapper.readTree(connection.getConnectionConfigJson()).path("datasets").path("product").path("path").asText())
            .isEqualTo("/api/products?limit=500");

        assertThat(plan.getRunnerMode()).isEqualTo("PLATFORM_MANAGED_AUTO");
        assertThat(plan.getStatus()).isEqualTo("ACTIVE");
        assertThat(plan.getSourceConnectionId()).isEqualTo(connection.getId());
        assertThat(plan.getSyncState()).isEqualTo("BOOTSTRAP_REQUIRED");
        var syncReasonCodes = objectMapper.readTree(plan.getSyncReasonCodesJson());
        assertThat(syncReasonCodes.isArray()).isTrue();
        assertThat(syncReasonCodes.get(0).asText()).isEqualTo("PLAN_CREATED");
        assertThat(syncReasonCodes.get(1).asText()).isEqualTo("ECOMMERCE_DEMO_ROLLOUT_SEEDED");

        assertThat(revision.getStatus()).isEqualTo("ACTIVE");
        assertThat(revision.getSourceConnectionId()).isEqualTo(connection.getId());
        var entityScope = objectMapper.readTree(revision.getEntityScopeJson());
        assertThat(entityScope.isArray()).isTrue();
        assertThat(entityScope.get(0).asText()).isEqualTo("policy");
        assertThat(entityScope.get(1).asText()).isEqualTo("product");
        assertThat(entityScope.get(2).asText()).isEqualTo("review");
    }
}
