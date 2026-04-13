package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDeletionOperationRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.auth.enabled=true",
    "platform.auth.header-name=X-PLATFORM-API-KEY",
    "platform.auth.operator-api-key=operator-test-key",
    "platform.auth.admin-api-key=admin-test-key",
    "platform.auth.bootstrap-admin-enabled=false",
    "platform.bootstrap.sample-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeploymentLifecycleOverviewIntegrationTest {

    private static final String PLATFORM_API_KEY_HEADER = "X-PLATFORM-API-KEY";
    private static final String ADMIN_API_KEY = "admin-test-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private DeploymentDeletionOperationRepository deletionOperationRepository;

    @Test
    void archiveRemovesDeploymentFromDefaultActiveListsAndPreservesOverview() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Customer Lifecycle Smoke", "dev", "dev-openai-lucene")
        ));

        mockMvc.perform(get("/api/deployments/overview")
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')].healthStatus", hasItem("DRAFT")))
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')].healthSummary",
                hasItem(startsWith("Draft configuration"))));

        mockMvc.perform(post("/api/deployments/{deploymentId}/archive", deployment.id())
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(deployment.id())))
            .andExpect(jsonPath("$.status", is("ARCHIVED")))
            .andExpect(jsonPath("$.healthStatus", is("ARCHIVED")))
            .andExpect(jsonPath("$.archivedAt", notNullValue()));

        mockMvc.perform(get("/api/deployments")
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')]").isEmpty());

        mockMvc.perform(get("/api/deployments").param("includeArchived", "true")
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')].status", hasItem("ARCHIVED")));

        mockMvc.perform(get("/api/deployments/overview").param("includeArchived", "true")
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')].healthStatus", hasItem("ARCHIVED")));

        mockMvc.perform(get("/api/platform/audit-events")
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.targetId=='" + deployment.id() + "')].action", hasItem("DEPLOYMENT_ARCHIVED")));
    }

    @Test
    void restoreReturnsDeploymentToActiveListsAndDeleteRemovesItPermanently() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Customer Lifecycle Restore", "stage", "dev-openai-lucene")
        ));

        mockMvc.perform(post("/api/deployments/{deploymentId}/archive", deployment.id())
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ARCHIVED")));

        mockMvc.perform(post("/api/deployments/{deploymentId}/restore", deployment.id())
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(deployment.id())))
            .andExpect(jsonPath("$.archivedAt").doesNotExist())
            .andExpect(jsonPath("$.status", is("DRAFT")));

        mockMvc.perform(get("/api/deployments")
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')].status", hasItem("DRAFT")));

        mockMvc.perform(post("/api/deployments/{deploymentId}/archive", deployment.id())
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ARCHIVED")));

        mockMvc.perform(delete("/api/deployments/{deploymentId}", deployment.id())
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.deploymentId", is(deployment.id())))
            .andExpect(jsonPath("$.status", is("QUEUED")));

        waitForDeletion(deployment.id());

        mockMvc.perform(get("/api/deployments").param("includeArchived", "true")
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')]").isEmpty());

        mockMvc.perform(get("/api/platform/audit-events")
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.targetId=='" + deployment.id() + "')].action", hasItem("DEPLOYMENT_RESTORED")))
            .andExpect(jsonPath("$[?(@.targetId=='" + deployment.id() + "')].action", hasItem("DEPLOYMENT_DELETE_QUEUED")))
            .andExpect(jsonPath("$[?(@.targetId=='" + deployment.id() + "')].action", hasItem("DEPLOYMENT_DELETE_COMPLETED")))
            .andExpect(jsonPath("$[?(@.targetId=='" + deployment.id() + "')].action", not(hasItem("DEPLOYMENT_PURGED"))));
    }

    private void waitForDeletion(String deploymentId) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            if (deploymentRepository.findById(deploymentId).isEmpty()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError(
            "Deployment was not deleted within the expected time window: " + deploymentId
                + " latestOperation="
                + deletionOperationRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId).stream()
                .findFirst()
                .map(op -> op.getStatus() + ":" + op.getErrorMessage())
                .orElse("none")
        );
    }

    private <T> T runAsAdmin(Supplier<T> supplier) {
        authenticateAdmin();
        try {
            return supplier.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticateAdmin() {
        PlatformPrincipal principal = new PlatformPrincipal(
            "admin@example.com",
            PlatformRole.PLATFORM_ADMIN,
            "Platform Admin",
            "SESSION"
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
