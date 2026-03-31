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
import static org.hamcrest.Matchers.is;
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
class DeploymentBulkOperationsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void adminCanBulkArchiveRestoreAndDeleteWithPerDeploymentResults() throws Exception {
        DeploymentSummary first = deploymentService.createDeployment(
            new CreateDeploymentRequest("Bulk Admin One", "dev", "dev-openai-lucene")
        );
        DeploymentSummary second = deploymentService.createDeployment(
            new CreateDeploymentRequest("Bulk Admin Two", "dev", "dev-openai-lucene")
        );
        Cookie adminSession = login("admin@example.com", "AdminPass123!");

        mockMvc.perform(post("/api/deployments/bulk/actions")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "action": "ARCHIVE",
                      "deploymentIds": ["%s", "%s"]
                    }
                    """.formatted(first.id(), second.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.action", is("ARCHIVE")))
            .andExpect(jsonPath("$.requestedCount", is(2)))
            .andExpect(jsonPath("$.succeededCount", is(2)))
            .andExpect(jsonPath("$.failedCount", is(0)));

        mockMvc.perform(post("/api/deployments/bulk/actions")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "action": "RESTORE",
                      "deploymentIds": ["%s"]
                    }
                    """.formatted(first.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.action", is("RESTORE")))
            .andExpect(jsonPath("$.succeededCount", is(1)))
            .andExpect(jsonPath("$.failedCount", is(0)));

        mockMvc.perform(post("/api/deployments/bulk/actions")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "action": "DELETE",
                      "deploymentIds": ["%s", "%s"]
                    }
                    """.formatted(first.id(), second.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.action", is("DELETE")))
            .andExpect(jsonPath("$.requestedCount", is(2)))
            .andExpect(jsonPath("$.succeededCount", is(1)))
            .andExpect(jsonPath("$.failedCount", is(1)))
            .andExpect(jsonPath("$.results[?(@.deploymentId=='%s')].status".formatted(first.id()), hasItem("FAILED")))
            .andExpect(jsonPath("$.results[?(@.deploymentId=='%s')].status".formatted(second.id()), hasItem("SUCCESS")));

        mockMvc.perform(get("/api/deployments/overview?includeArchived=true")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].id", hasItem(first.id())))
            .andExpect(jsonPath("$[*].id", not(hasItem(second.id()))));
    }

    @Test
    void operatorCannotUseBulkEndpoint() throws Exception {
        createUser(
            "usr-bulk-operator",
            "bulk-operator@example.com",
            "Bulk Operator",
            "PLATFORM_OPERATOR",
            "ACTIVE",
            "OperatorPass123!"
        );
        Cookie operatorSession = login("bulk-operator@example.com", "OperatorPass123!");

        mockMvc.perform(post("/api/deployments/bulk/actions")
                .cookie(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "action": "ARCHIVE",
                      "deploymentIds": ["dep-missing"]
                    }
                    """))
            .andExpect(status().isForbidden());
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
