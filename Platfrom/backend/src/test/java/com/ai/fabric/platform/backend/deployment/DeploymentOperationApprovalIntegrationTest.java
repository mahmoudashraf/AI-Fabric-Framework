package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentAssignmentEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.UUID;

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
class DeploymentOperationApprovalIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private DeploymentAssignmentRepository deploymentAssignmentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void operatorMustUseApprovedRequestForProtectedApply() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Approval Apply", "dev", "dev-openai-lucene")
        );
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        DeploymentVersionSummary version = deploymentService.publishDraft(draft.id());
        PlatformUserEntity operator = createUser(
            "approval-operator@example.com",
            "Approval Operator",
            "PLATFORM_OPERATOR",
            "OperatorPass123!"
        );
        assignDeployment(deployment.id(), operator.getId(), "DEPLOYMENT_OPERATOR");

        Cookie adminSession = login("admin@example.com", "AdminPass123!");
        Cookie operatorSession = login("approval-operator@example.com", "OperatorPass123!");

        mockMvc.perform(put("/api/deployments/{deploymentId}/guardrails", deployment.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approvalRequiredForApply": true,
                      "approvalRequiredForDelete": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalRequiredForApply", is(true)))
            .andExpect(jsonPath("$.approvalRequiredForDelete", is(false)));

        mockMvc.perform(post("/api/deployments/{deploymentId}/apply/{versionId}", deployment.id(), version.id())
                .cookie(operatorSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message", is("This deployment action is protected. Request and approve an operation approval first.")));

        String approvalId = createApprovalRequest(
            deployment.id(),
            operatorSession,
            """
                {
                  "operationType": "APPLY_VERSION",
                  "targetVersionId": "%s",
                  "reason": "Need to promote the verified version for operator validation."
                }
                """.formatted(version.id())
        );

        mockMvc.perform(post("/api/deployment-approvals/{approvalId}/approve", approvalId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Approved for controlled operator rollout.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("APPROVED")));

        mockMvc.perform(post("/api/deployments/{deploymentId}/apply/{versionId}?approvalId={approvalId}",
                    deployment.id(), version.id(), approvalId)
                .cookie(operatorSession))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("APPLY_REQUESTED")));

        mockMvc.perform(get("/api/deployments/{deploymentId}/approvals", deployment.id())
                .cookie(operatorSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='%s')].status".formatted(approvalId), hasItem("CONSUMED")));
    }

    @Test
    void operatorMustUseApprovedRequestForProtectedDelete() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Approval Delete", "dev", "dev-openai-lucene")
        );
        deploymentService.archiveDeployment(deployment.id());
        PlatformUserEntity operator = createUser(
            "delete-operator@example.com",
            "Delete Operator",
            "PLATFORM_OPERATOR",
            "DeletePass123!"
        );
        assignDeployment(deployment.id(), operator.getId(), "DEPLOYMENT_OPERATOR");

        Cookie adminSession = login("admin@example.com", "AdminPass123!");
        Cookie operatorSession = login("delete-operator@example.com", "DeletePass123!");

        mockMvc.perform(put("/api/deployments/{deploymentId}/guardrails", deployment.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "approvalRequiredForApply": false,
                      "approvalRequiredForDelete": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.approvalRequiredForApply", is(false)))
            .andExpect(jsonPath("$.approvalRequiredForDelete", is(true)));

        mockMvc.perform(delete("/api/deployments/{deploymentId}", deployment.id())
                .cookie(operatorSession))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message", is("This deployment action is protected. Request and approve an operation approval first.")));

        String approvalId = createApprovalRequest(
            deployment.id(),
            operatorSession,
            """
                {
                  "operationType": "DELETE_DEPLOYMENT",
                  "reason": "Deployment is archived and needs full cleanup after validation."
                }
                """
        );

        mockMvc.perform(post("/api/deployment-approvals/{approvalId}/approve", approvalId)
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"Approved for cleanup.\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("APPROVED")));

        mockMvc.perform(delete("/api/deployments/{deploymentId}?approvalId={approvalId}", deployment.id(), approvalId)
                .cookie(operatorSession))
            .andExpect(status().isNoContent());
    }

    private String createApprovalRequest(String deploymentId, Cookie sessionCookie, String payload) throws Exception {
        String response = mockMvc.perform(post("/api/deployments/{deploymentId}/approvals", deploymentId)
                .cookie(sessionCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("PENDING")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.path("id").asText();
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

    private PlatformUserEntity createUser(String email,
                                          String displayName,
                                          String role,
                                          String password) {
        return platformUserRepository.findByEmailIgnoreCase(email)
            .orElseGet(() -> {
                PlatformUserEntity user = new PlatformUserEntity();
                Instant now = Instant.now();
                user.setId("usr-" + UUID.randomUUID().toString().substring(0, 8));
                user.setEmail(email);
                user.setDisplayName(displayName);
                user.setPasswordHash(passwordEncoder.encode(password));
                user.setRole(role);
                user.setStatus("ACTIVE");
                user.setCreatedAt(now);
                user.setUpdatedAt(now);
                return platformUserRepository.save(user);
            });
    }

    private void assignDeployment(String deploymentId, String userId, String assignmentRole) {
        if (deploymentAssignmentRepository.findByUserIdAndDeploymentId(userId, deploymentId).isPresent()) {
            return;
        }
        DeploymentAssignmentEntity assignment = new DeploymentAssignmentEntity();
        Instant now = Instant.now();
        assignment.setId("asg-" + UUID.randomUUID().toString().substring(0, 8));
        assignment.setDeploymentId(deploymentId);
        assignment.setUserId(userId);
        assignment.setAssignmentRole(assignmentRole);
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        deploymentAssignmentRepository.save(assignment);
    }
}
