package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
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

import java.util.List;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class DeploymentPocWorkspaceIntegrationTest {

    private static final String PLATFORM_API_KEY_HEADER = "X-PLATFORM-API-KEY";
    private static final String ADMIN_API_KEY = "admin-test-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Test
    void pocWorkspaceReturnsMigrationGuideAndReadinessChecks() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("POC Workspace", "dev", "dev-openai-lucene")
        ));

        mockMvc.perform(get("/api/deployments/{deploymentId}/poc", deployment.id())
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataset.profileId", is("unconfigured")))
            .andExpect(jsonPath("$.migration.suggestedDatasetLabel", is("Operator POC import")))
            .andExpect(jsonPath("$.migration.maxRecordsPerRun", is(100)))
            .andExpect(jsonPath("$.migration.maxContentLength", is(16000)))
            .andExpect(jsonPath("$.migration.defaultVectorSpace", is("default")))
            .andExpect(jsonPath("$.migration.supportedVectorSpaces[0]", is("default")))
            .andExpect(jsonPath("$.migration.supportedSources.length()", is(3)))
            .andExpect(jsonPath("$.migration.supportedSources[0].key", is("TEMPLATE_SAMPLE")))
            .andExpect(jsonPath("$.migration.readinessChecks.length()", is(4)))
            .andExpect(jsonPath("$.migration.readinessChecks[0].key", is("IMPORT_TRANSPORT")))
            .andExpect(jsonPath("$.migration.readinessChecks[0].status", is("BLOCKED")))
            .andExpect(jsonPath("$.migration.readinessChecks[1].key", is("CHAT_AUTH_POSTURE")))
            .andExpect(jsonPath("$.migration.readinessChecks[1].status", is("BLOCKED")));
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
