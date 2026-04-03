package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.auth.enabled=true",
    "platform.auth.api-key-enabled=false",
    "platform.auth.session-enabled=true",
    "platform.auth.session-cookie-name=platform_session",
    "platform.auth.session-cookie-secure=false",
    "platform.auth.bootstrap-admin-enabled=true",
    "platform.auth.bootstrap-admin-email=admin@example.com",
    "platform.auth.bootstrap-admin-password=AdminPass123!",
    "platform.auth.bootstrap-admin-display-name=Platform Admin",
    "platform.bootstrap.sample-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeploymentAssignmentVisibilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void operatorOnlySeesAssignedDeploymentsAndArtifacts() throws Exception {
        DeploymentSummary visibleDeployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Assigned Deployment", "dev", "dev-openai-lucene")
        );
        DeploymentSummary hiddenDeployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Hidden Deployment", "dev", "dev-openai-lucene")
        );

        DeploymentVersionSummary visibleVersion = publish(visibleDeployment.id());
        DeploymentVersionSummary hiddenVersion = publish(hiddenDeployment.id());

        createUser(
            "usr-operator-assigned",
            "operator-assigned@example.com",
            "Assigned Operator",
            "PLATFORM_OPERATOR",
            "ACTIVE",
            "OperatorPass123!"
        );

        Cookie adminSession = login("admin@example.com", "AdminPass123!");
        String operatorId = platformUserRepository.findByEmailIgnoreCase("operator-assigned@example.com")
            .orElseThrow()
            .getId();

        mockMvc.perform(post("/api/deployments/{deploymentId}/assignments", visibleDeployment.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s",
                      "assignmentRole": "DEPLOYMENT_OPERATOR"
                    }
                    """.formatted(operatorId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.deploymentId", is(visibleDeployment.id())))
            .andExpect(jsonPath("$.userId", is(operatorId)))
            .andExpect(jsonPath("$.assignmentRole", is("DEPLOYMENT_OPERATOR")));

        Cookie operatorSession = login("operator-assigned@example.com", "OperatorPass123!");

        mockMvc.perform(get("/api/deployments")
                .cookie(operatorSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is(visibleDeployment.id())));

        mockMvc.perform(get("/api/deployments/overview")
                .cookie(operatorSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id", is(visibleDeployment.id())));

        mockMvc.perform(get("/api/deployments/{deploymentId}/workspace", visibleDeployment.id())
                .cookie(operatorSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deployment.id", is(visibleDeployment.id())));

        mockMvc.perform(get("/api/deployments/{deploymentId}/workspace", hiddenDeployment.id())
                .cookie(operatorSession))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/deployments/{deploymentId}/assignments", visibleDeployment.id())
                .cookie(operatorSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].userEmail", is("operator-assigned@example.com")));

        mockMvc.perform(get("/api/deployments/{deploymentId}/assignments", hiddenDeployment.id())
                .cookie(operatorSession))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/deployments/{deploymentId}/versions/{versionId}/artifacts",
                    visibleDeployment.id(), visibleVersion.id())
                .cookie(operatorSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(visibleDeployment.id())));

        mockMvc.perform(get("/api/deployments/{deploymentId}/versions/{versionId}/artifacts",
                    hiddenDeployment.id(), hiddenVersion.id())
                .cookie(operatorSession))
            .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/deployments/{deploymentId}/assignments", visibleDeployment.id())
                .cookie(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s",
                      "assignmentRole": "DEPLOYMENT_VIEWER"
                    }
                    """.formatted(operatorId)))
            .andExpect(status().isForbidden());
    }

    private DeploymentVersionSummary publish(String deploymentId) {
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deploymentId);
        return deploymentService.publishDraft(draft.id());
    }

    private Cookie login(String email, String password) throws Exception {
        String cookieHeader = mockMvc.perform(post("/api/platform/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Set-Cookie");

        String sessionValue = cookieHeader
            .substring(0, cookieHeader.indexOf(';'))
            .replace("platform_session=", "");
        return new Cookie("platform_session", sessionValue);
    }

    private void createUser(String id,
                            String email,
                            String displayName,
                            String role,
                            String status,
                            String password) {
        if (platformUserRepository.findByEmailIgnoreCase(email).isPresent()) {
            return;
        }
        PlatformUserEntity user = new PlatformUserEntity();
        Instant now = Instant.now();
        user.setId(id);
        user.setEmail(email);
        user.setDisplayName(displayName);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setStatus(status);
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        platformUserRepository.save(user);
    }
}
