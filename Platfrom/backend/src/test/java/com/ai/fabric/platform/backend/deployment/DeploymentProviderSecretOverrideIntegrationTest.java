package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDeletionOperationRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.secret.repository.DeploymentProviderSecretBindingRepository;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class DeploymentProviderSecretOverrideIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private PlatformSecretRepository platformSecretRepository;

    @Autowired
    private DeploymentProviderSecretBindingRepository bindingRepository;

    @Autowired
    private DeploymentDeletionOperationRepository deletionOperationRepository;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void platformAdminCanManageOverrideSecretsAndDeploymentAdminCanBindAndUnbind() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Override Binding Smoke", "dev", "dev-openai-lucene")
        );
        Cookie adminSession = login("admin@example.com", "AdminPass123!");

        createUser(
            "usr-secret-deployment-admin",
            "secret-deployment-admin@example.com",
            "Secret Deployment Admin",
            "PLATFORM_OPERATOR",
            "ACTIVE",
            "SecretAdminPass123!"
        );
        String deploymentAdminUserId = platformUserRepository.findByEmailIgnoreCase("secret-deployment-admin@example.com")
            .orElseThrow()
            .getId();
        assign(adminSession, deployment.id(), deploymentAdminUserId, "DEPLOYMENT_ADMIN");
        Cookie deploymentAdminSession = login("secret-deployment-admin@example.com", "SecretAdminPass123!");

        mockMvc.perform(put("/api/platform/secrets/deployment-overrides/{name}", "DEPLOYMENT_OPENAI_OVERRIDE_ONE")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "OPENAI_API_KEY",
                      "value": "override-one-value",
                      "deploymentId": "%s",
                      "cleanupPolicy": "DELETE_ON_HARD_DELETE"
                    }
                    """.formatted(deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name", is("DEPLOYMENT_OPENAI_OVERRIDE_ONE")))
            .andExpect(jsonPath("$.secretPurpose", is("OPENAI_API_KEY")))
            .andExpect(jsonPath("$.deploymentId", is(deployment.id())))
            .andExpect(jsonPath("$.cleanupPolicy", is("DELETE_ON_HARD_DELETE")));

        mockMvc.perform(get("/api/platform/secrets/deployment-overrides")
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].name", hasItem("DEPLOYMENT_OPENAI_OVERRIDE_ONE")));

        mockMvc.perform(put("/api/platform/secrets/deployment-overrides/{name}", "DEPLOYMENT_OPENAI_OVERRIDE_TWO")
                .cookie(deploymentAdminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "OPENAI_API_KEY",
                      "value": "should-fail"
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/deployments/{deploymentId}/provider-secret-bindings", deployment.id())
                .cookie(deploymentAdminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "OPENAI_API_KEY",
                      "bindingMode": "REQUIRE_OVERRIDE",
                      "secretName": "DEPLOYMENT_OPENAI_OVERRIDE_ONE"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deployment.id())))
            .andExpect(jsonPath("$.secretPurpose", is("OPENAI_API_KEY")))
            .andExpect(jsonPath("$.bindingMode", is("REQUIRE_OVERRIDE")))
            .andExpect(jsonPath("$.secretName", is("DEPLOYMENT_OPENAI_OVERRIDE_ONE")))
            .andExpect(jsonPath("$.effectiveResolution.resolved", is(true)))
            .andExpect(jsonPath("$.effectiveResolution.scopeType", is("DEPLOYMENT_OVERRIDE")))
            .andExpect(jsonPath("$.effectiveResolution.reasonCode", is("DEPLOYMENT_OVERRIDE_PRESENT")));

        mockMvc.perform(get("/api/deployments/{deploymentId}/provider-secret-bindings", deployment.id())
                .cookie(deploymentAdminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deployment.id())))
            .andExpect(jsonPath("$.supportedPurposes", hasItem("OPENAI_API_KEY")))
            .andExpect(jsonPath("$.availableOverrideSecrets[*].name", hasItem("DEPLOYMENT_OPENAI_OVERRIDE_ONE")))
            .andExpect(jsonPath("$.bindings[0].secretPurpose", is("OPENAI_API_KEY")));

        mockMvc.perform(delete("/api/deployments/{deploymentId}/provider-secret-bindings/{secretPurpose}", deployment.id(), "OPENAI_API_KEY")
                .cookie(deploymentAdminSession))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/deployments/{deploymentId}/provider-secret-bindings", deployment.id())
                .cookie(deploymentAdminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bindings.length()", is(0)));
    }

    @Test
    void bindingValidationAndHardDeleteCleanupFollowOwnershipRules() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Override Cleanup Smoke", "dev", "dev-openai-lucene")
        );
        Cookie adminSession = login("admin@example.com", "AdminPass123!");

        createUser(
            "usr-secret-editor",
            "secret-editor@example.com",
            "Secret Editor",
            "PLATFORM_OPERATOR",
            "ACTIVE",
            "SecretEditorPass123!"
        );
        String editorUserId = platformUserRepository.findByEmailIgnoreCase("secret-editor@example.com")
            .orElseThrow()
            .getId();
        assign(adminSession, deployment.id(), editorUserId, "DEPLOYMENT_EDITOR");
        Cookie editorSession = login("secret-editor@example.com", "SecretEditorPass123!");

        mockMvc.perform(put("/api/deployments/{deploymentId}/provider-secret-bindings", deployment.id())
                .cookie(editorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "OPENAI_API_KEY",
                      "bindingMode": "ALLOW_STANDARD_PRECEDENCE",
                      "secretName": "DEPLOYMENT_OPENAI_OVERRIDE_CLEANUP"
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/deployments/{deploymentId}/provider-secret-bindings", deployment.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "MILVUS_RUNTIME_CREDENTIALS",
                      "bindingMode": "REQUIRE_OVERRIDE",
                      "secretName": "DEPLOYMENT_MILVUS_USERNAME_ONLY"
                    }
                    """))
            .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/platform/secrets/deployment-overrides/{name}", "DEPLOYMENT_OPENAI_OVERRIDE_CLEANUP")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "OPENAI_API_KEY",
                      "value": "cleanup-owned-value",
                      "deploymentId": "%s",
                      "cleanupPolicy": "DELETE_ON_HARD_DELETE"
                    }
                    """.formatted(deployment.id())))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/platform/secrets/deployment-overrides/{name}", "DEPLOYMENT_WEAVIATE_OVERRIDE_SHARED")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "WEAVIATE_API_KEY",
                      "value": "shared-value",
                      "cleanupPolicy": "KEEP"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/deployments/{deploymentId}/provider-secret-bindings", deployment.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "OPENAI_API_KEY",
                      "bindingMode": "REQUIRE_OVERRIDE",
                      "secretName": "DEPLOYMENT_OPENAI_OVERRIDE_CLEANUP"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/deployments/{deploymentId}/provider-secret-bindings", deployment.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "WEAVIATE_API_KEY",
                      "bindingMode": "ALLOW_STANDARD_PRECEDENCE",
                      "secretName": "DEPLOYMENT_WEAVIATE_OVERRIDE_SHARED"
                    }
                    """))
            .andExpect(status().isOk());

        deploymentService.archiveDeployment(deployment.id());

        mockMvc.perform(delete("/api/deployments/{deploymentId}", deployment.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "hardDelete": true,
                      "reason": "override cleanup integration"
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status", is("QUEUED")));

        waitForDeletion(deployment.id());

        assertThat(bindingRepository.findByDeploymentIdOrderBySecretPurposeAsc(deployment.id())).isEmpty();
        assertThat(platformSecretRepository.findById("DEPLOYMENT_OPENAI_OVERRIDE_CLEANUP")).isEmpty();
        assertThat(platformSecretRepository.findById("DEPLOYMENT_WEAVIATE_OVERRIDE_SHARED")).isPresent();

        var deletionOperation = deletionOperationRepository.findTop200ByOrderByCreatedAtDesc().stream()
            .filter(item -> deployment.id().equals(item.getDeploymentId()))
            .findFirst()
            .orElseThrow();
        assertThat(deletionOperation.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(deletionOperation.getResultDetailsJson()).contains("providerSecretOverrideCleanup");
        assertThat(deletionOperation.getResultDetailsJson()).contains("DEPLOYMENT_OPENAI_OVERRIDE_CLEANUP");
        assertThat(deletionOperation.getResultDetailsJson()).contains("DEPLOYMENT_WEAVIATE_OVERRIDE_SHARED");
    }

    @Test
    void workspaceEndpointsReflectFallbackAndRequireOverrideModes() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Override Resolution Modes", "dev", "dev-openai-lucene")
        );
        Cookie adminSession = login("admin@example.com", "AdminPass123!");

        mockMvc.perform(put("/api/platform/secrets/{name}", "OPENAI_API_KEY")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "global-openai-value"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/platform/secrets/deployment-overrides/{name}", "DEPLOYMENT_OPENAI_OVERRIDE_FALLBACK")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "OPENAI_API_KEY",
                      "value": "override-value",
                      "deploymentId": "%s",
                      "cleanupPolicy": "DELETE_ON_HARD_DELETE"
                    }
                    """.formatted(deployment.id())))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/deployments/{deploymentId}/provider-secret-bindings", deployment.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "OPENAI_API_KEY",
                      "bindingMode": "ALLOW_STANDARD_PRECEDENCE",
                      "secretName": "DEPLOYMENT_OPENAI_OVERRIDE_FALLBACK"
                    }
                    """))
            .andExpect(status().isOk());

        platformSecretRepository.deleteById("DEPLOYMENT_OPENAI_OVERRIDE_FALLBACK");

        mockMvc.perform(get("/api/deployments/{deploymentId}/provider-secret-bindings", deployment.id())
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bindings[0].bindingMode", is("ALLOW_STANDARD_PRECEDENCE")))
            .andExpect(jsonPath("$.bindings[0].effectiveResolution.resolved", is(true)))
            .andExpect(jsonPath("$.bindings[0].effectiveResolution.scopeType", is("GLOBAL_PLATFORM")))
            .andExpect(jsonPath("$.bindings[0].effectiveResolution.fallbackUsed", is(true)))
            .andExpect(jsonPath("$.bindings[0].effectiveResolution.reasonCode", is("GLOBAL_DEFAULT_PRESENT")));

        mockMvc.perform(get("/api/deployments/{deploymentId}/secret-usage", deployment.id())
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.secrets[?(@.secretName=='OPENAI_API_KEY')].present", is(java.util.List.of(true))))
            .andExpect(jsonPath("$.secrets[?(@.secretName=='OPENAI_API_KEY')].source", is(java.util.List.of("GLOBAL_PLATFORM"))))
            .andExpect(jsonPath("$.secrets[?(@.secretName=='OPENAI_API_KEY')].effectiveResolution.reasonCode", is(java.util.List.of("GLOBAL_DEFAULT_PRESENT"))));

        mockMvc.perform(put("/api/platform/secrets/deployment-overrides/{name}", "DEPLOYMENT_OPENAI_OVERRIDE_REQUIRED")
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "OPENAI_API_KEY",
                      "value": "required-override-value",
                      "deploymentId": "%s",
                      "cleanupPolicy": "DELETE_ON_HARD_DELETE"
                    }
                    """.formatted(deployment.id())))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/deployments/{deploymentId}/provider-secret-bindings", deployment.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "OPENAI_API_KEY",
                      "bindingMode": "REQUIRE_OVERRIDE",
                      "secretName": "DEPLOYMENT_OPENAI_OVERRIDE_REQUIRED"
                    }
                    """))
            .andExpect(status().isOk());

        platformSecretRepository.deleteById("DEPLOYMENT_OPENAI_OVERRIDE_REQUIRED");

        mockMvc.perform(get("/api/deployments/{deploymentId}/provider-secret-bindings", deployment.id())
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.bindings[0].bindingMode", is("REQUIRE_OVERRIDE")))
            .andExpect(jsonPath("$.bindings[0].effectiveResolution.resolved", is(false)))
            .andExpect(jsonPath("$.bindings[0].effectiveResolution.reasonCode", is("DEPLOYMENT_OVERRIDE_REQUIRED_MISSING")));

        mockMvc.perform(get("/api/deployments/{deploymentId}/secret-usage", deployment.id())
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.secrets[?(@.secretName=='OPENAI_API_KEY')].present", is(java.util.List.of(false))))
            .andExpect(jsonPath("$.secrets[?(@.secretName=='OPENAI_API_KEY')].status", is(java.util.List.of("MISSING"))))
            .andExpect(jsonPath("$.secrets[?(@.secretName=='OPENAI_API_KEY')].effectiveResolution.reasonCode", is(java.util.List.of("DEPLOYMENT_OVERRIDE_REQUIRED_MISSING"))));

        mockMvc.perform(get("/api/deployments/{deploymentId}/provider-connectivity", deployment.id())
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.effectiveSecretResolutions[?(@.secretPurpose=='OPENAI_API_KEY')].reasonCode",
                is(java.util.List.of("DEPLOYMENT_OVERRIDE_REQUIRED_MISSING"))));
    }

    private void waitForDeletion(String deploymentId) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            if (deploymentRepository.findById(deploymentId).isEmpty()) {
                return;
            }
            Thread.sleep(100);
        }
        DeploymentEntity lingeringDeployment = deploymentRepository.findById(deploymentId).orElse(null);
        throw new AssertionError("Deployment was not deleted within the expected time window: " + deploymentId
            + (lingeringDeployment == null ? "" : " status=" + lingeringDeployment.getDeletionStatus()));
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
