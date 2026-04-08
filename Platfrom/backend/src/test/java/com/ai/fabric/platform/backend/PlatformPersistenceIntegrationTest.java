package com.ai.fabric.platform.backend;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
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

    @BeforeEach
    void authenticate() {
        PlatformTestSecurity.authenticateAsPlatformAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        PlatformTestSecurity.clearAuthentication();
    }

    @Test
    void flywayManagedSchemaSupportsDeploymentDraftAndVersionLifecycle() {
        Integer appliedMigrations = jdbcTemplate.queryForObject(
            "select count(*) from flyway_schema_history where success = true",
            Integer.class
        );
        assertThat(appliedMigrations).isNotNull();
        assertThat(appliedMigrations).isGreaterThanOrEqualTo(1);

        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Persistence Smoke", "dev", "dev-openai-lucene")
        );
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        DeploymentVersionSummary version = deploymentService.publishDraft(draft.id());

        assertThat(deployment.id()).startsWith("dep-");
        assertThat(draft.revisionNumber()).isEqualTo(1);
        assertThat(version.versionLabel()).isEqualTo("v1");
        assertThat(version.deploymentId()).isEqualTo(deployment.id());
    }
}
