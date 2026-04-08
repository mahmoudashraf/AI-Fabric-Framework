package com.ai.fabric.platform.backend;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class PlatformPersistenceIntegrationTest {

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void authenticate() {
        PlatformTestSecurity.authenticateAsPlatformAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        PlatformTestSecurity.clearAuthentication();
    }
    @Test
    void flywayManagedSchemaSupportsDeploymentDraftAndVersionLifecycle() throws Exception {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where success = true",
            Integer.class
        );
        assertThat(appliedMigrations).isNotNull();
        assertThat(appliedMigrations).isGreaterThanOrEqualTo(27);

        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Persistence Smoke", "dev", "dev-openai-lucene")
        );
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        var shellConfig = objectMapper.createObjectNode();
        shellConfig.putObject("branding").put("assistantName", "Marketplace Shell");
        shellConfig.putArray("enabledModuleIds").add("docs").add("products");
        var knowledgeSourceConfig = objectMapper.createObjectNode();
        knowledgeSourceConfig.putArray("sources")
            .add(objectMapper.createObjectNode()
                .put("sourceType", "shared-index")
                .put("sourceKey", "commerce-catalog"));

        deploymentService.updateDraft(
            draft.id(),
            new UpdateDeploymentDraftRequest(null, null, null, null, null, null, shellConfig, knowledgeSourceConfig)
        );

        DeploymentDraftResponse updatedDraft = deploymentService.getActiveDraftForDeployment(deployment.id());
        DeploymentVersionSummary version = deploymentService.publishDraft(updatedDraft.id());
        DeploymentDraftResponse nextDraft = deploymentService.getActiveDraftForDeployment(deployment.id());

        String publishedShellConfigJson = jdbcTemplate.queryForObject(
            "select shell_config_json from platform_deployment_versions where id = ?",
            String.class,
            version.id()
        );
        String nextDraftShellConfigJson = jdbcTemplate.queryForObject(
            "select shell_config_json from platform_deployment_drafts where id = ?",
            String.class,
            nextDraft.id()
        );
        String publishedKnowledgeSourceConfigJson = jdbcTemplate.queryForObject(
            "select knowledge_source_config_json from platform_deployment_versions where id = ?",
            String.class,
            version.id()
        );
        String nextDraftKnowledgeSourceConfigJson = jdbcTemplate.queryForObject(
            "select knowledge_source_config_json from platform_deployment_drafts where id = ?",
            String.class,
            nextDraft.id()
        );

        assertThat(deployment.id()).startsWith("dep-");
        assertThat(updatedDraft.revisionNumber()).isEqualTo(1);
        assertThat(updatedDraft.shellConfig()).isEqualTo(shellConfig);
        assertThat(version.versionLabel()).isEqualTo("v1");
        assertThat(version.deploymentId()).isEqualTo(deployment.id());
        assertThat(nextDraft.revisionNumber()).isEqualTo(2);
        assertThat(nextDraft.shellConfig()).isEqualTo(shellConfig);
        assertThat(nextDraft.knowledgeSourceConfig()).isEqualTo(knowledgeSourceConfig);
        assertThat(objectMapper.readTree(publishedShellConfigJson)).isEqualTo(shellConfig);
        assertThat(objectMapper.readTree(nextDraftShellConfigJson)).isEqualTo(shellConfig);
        assertThat(objectMapper.readTree(publishedKnowledgeSourceConfigJson)).isEqualTo(knowledgeSourceConfig);
        assertThat(objectMapper.readTree(nextDraftKnowledgeSourceConfigJson)).isEqualTo(knowledgeSourceConfig);
    }
}
