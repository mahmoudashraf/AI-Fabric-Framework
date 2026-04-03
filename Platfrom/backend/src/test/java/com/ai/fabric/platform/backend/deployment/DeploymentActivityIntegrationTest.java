package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
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
class DeploymentActivityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void deploymentActivityEndpointReturnsOnlySelectedDeploymentEvents() throws Exception {
        DeploymentSummary first = deploymentService.createDeployment(
            new CreateDeploymentRequest("Activity Feed One", "dev", "dev-openai-lucene")
        );
        DeploymentSummary second = deploymentService.createDeployment(
            new CreateDeploymentRequest("Activity Feed Two", "dev", "dev-openai-lucene")
        );

        createUser("usr-activity-viewer", "activity-viewer@example.com", "Activity Viewer", "PLATFORM_OPERATOR", "ACTIVE", "ViewerPass123!");

        Cookie adminSession = login("admin@example.com", "AdminPass123!");
        String viewerId = platformUserRepository.findByEmailIgnoreCase("activity-viewer@example.com").orElseThrow().getId();

        assign(adminSession, first.id(), viewerId, "DEPLOYMENT_VIEWER");
        mockMvc.perform(post("/api/deployments/{deploymentId}/archive", first.id()).cookie(adminSession))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/deployments/{deploymentId}/restore", first.id()).cookie(adminSession))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/deployments/{deploymentId}/archive", second.id()).cookie(adminSession))
            .andExpect(status().isOk());

        Cookie viewerSession = login("activity-viewer@example.com", "ViewerPass123!");

        mockMvc.perform(get("/api/deployments/{deploymentId}/activity", first.id())
                .cookie(viewerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("DEPLOYMENT_CREATED")))
            .andExpect(jsonPath("$[*].action", hasItem("DEPLOYMENT_ARCHIVED")))
            .andExpect(jsonPath("$[*].action", hasItem("DEPLOYMENT_RESTORED")))
            .andExpect(jsonPath("$[?(@.action=='DEPLOYMENT_ASSIGNMENT_UPSERTED')].details.deploymentId", hasItem(first.id())))
            .andExpect(jsonPath("$[*].targetId", not(hasItem(second.id()))))
            .andExpect(jsonPath("$[?(@.details.deploymentId=='%s')].details.deploymentId".formatted(first.id()), hasItem(first.id())));
    }

    private void assign(Cookie adminSession, String deploymentId, String userId, String assignmentRole) throws Exception {
        mockMvc.perform(post("/api/deployments/{deploymentId}/assignments", deploymentId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s",
                      "assignmentRole": "%s"
                    }
                    """.formatted(userId, assignmentRole)))
            .andExpect(status().isCreated());
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
