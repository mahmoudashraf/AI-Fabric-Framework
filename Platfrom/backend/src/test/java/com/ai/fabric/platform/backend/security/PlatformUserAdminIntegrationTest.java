package com.ai.fabric.platform.backend.security;

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
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class PlatformUserAdminIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void adminCanListCreateUpdateAndResetUsers() throws Exception {
        Cookie adminSession = login("admin@example.com", "AdminPass123!");

        mockMvc.perform(get("/api/platform/users")
                .cookie(adminSession))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/platform/users")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "operator-primary@example.com",
                      "displayName": "Operations User",
                      "password": "OperatorPass123!",
                      "role": "PLATFORM_OPERATOR"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.email", is("operator-primary@example.com")))
            .andExpect(jsonPath("$.role", is("PLATFORM_OPERATOR")))
            .andExpect(jsonPath("$.status", is("ACTIVE")));

        String operatorId = platformUserRepository.findByEmailIgnoreCase("operator-primary@example.com")
            .orElseThrow()
            .getId();

        mockMvc.perform(put("/api/platform/users/{userId}", operatorId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName": "Operations Lead",
                      "role": "PLATFORM_ADMIN",
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.displayName", is("Operations Lead")))
            .andExpect(jsonPath("$.role", is("PLATFORM_ADMIN")))
            .andExpect(jsonPath("$.status", is("ACTIVE")));

        mockMvc.perform(post("/api/platform/users/{userId}/reset-password", operatorId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "password": "OperatorReset123!"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(operatorId)));

        PlatformUserEntity operator = platformUserRepository.findById(operatorId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(
            passwordEncoder.matches("OperatorReset123!", operator.getPasswordHash())
        );

        mockMvc.perform(get("/api/platform/users")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.email=='operator-primary@example.com')].displayName", hasItem("Operations Lead")))
            .andExpect(jsonPath("$[?(@.email=='operator-primary@example.com')].role", hasItem("PLATFORM_ADMIN")));
    }

    @Test
    void operatorCannotAccessUserAdministrationEndpoints() throws Exception {
        createUser(
            "usr-operator-readonly",
            "operator-readonly@example.com",
            "Platform Operator",
            "PLATFORM_OPERATOR",
            "ACTIVE",
            "OperatorPass123!"
        );
        Cookie operatorSession = login("operator-readonly@example.com", "OperatorPass123!");

        mockMvc.perform(get("/api/platform/users")
                .cookie(operatorSession))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUserAccessOverviewForSelectedDeployment() throws Exception {
        Cookie adminSession = login("admin@example.com", "AdminPass123!");
        DeploymentSummary firstDeployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Overview Deployment", "dev", "dev-openai-lucene")
        );
        DeploymentSummary secondDeployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Secondary Deployment", "dev", "dev-openai-lucene")
        );

        mockMvc.perform(post("/api/platform/users")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "overview-operator@example.com",
                      "displayName": "Overview Operator",
                      "password": "OperatorPass123!",
                      "role": "PLATFORM_OPERATOR"
                    }
                    """))
            .andExpect(status().isCreated());

        String operatorId = platformUserRepository.findByEmailIgnoreCase("overview-operator@example.com")
            .orElseThrow()
            .getId();

        mockMvc.perform(post("/api/deployments/{deploymentId}/assignments", firstDeployment.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s",
                      "assignmentRole": "DEPLOYMENT_EDITOR"
                    }
                    """.formatted(operatorId)))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/deployments/{deploymentId}/assignments", secondDeployment.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "userId": "%s",
                      "assignmentRole": "DEPLOYMENT_VIEWER"
                    }
                    """.formatted(operatorId)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/platform/users/access-overview")
                .param("deploymentId", firstDeployment.id())
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.email=='overview-operator@example.com')].assignmentCount", hasItem(2)))
            .andExpect(jsonPath("$[?(@.email=='overview-operator@example.com')].editorAssignmentCount", hasItem(1)))
            .andExpect(jsonPath("$[?(@.email=='overview-operator@example.com')].viewerAssignmentCount", hasItem(1)))
            .andExpect(jsonPath("$[?(@.email=='overview-operator@example.com')].selectedDeploymentAssignment.assignmentRole",
                hasItem("DEPLOYMENT_EDITOR")))
            .andExpect(jsonPath("$[?(@.email=='overview-operator@example.com')].assignedDeployments.length()", hasItem(2)));
    }

    @Test
    void lastActiveAdminCannotRemoveOwnAdminAccess() throws Exception {
        Cookie adminSession = login("admin@example.com", "AdminPass123!");
        String adminUserId = platformUserRepository.findByEmailIgnoreCase("admin@example.com")
            .orElseThrow()
            .getId();

        mockMvc.perform(put("/api/platform/users/{userId}", adminUserId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "displayName": "Platform Admin",
                      "role": "PLATFORM_OPERATOR",
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isBadRequest());

        PlatformUserEntity admin = platformUserRepository.findById(adminUserId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("PLATFORM_ADMIN", admin.getRole());
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", admin.getStatus());
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
