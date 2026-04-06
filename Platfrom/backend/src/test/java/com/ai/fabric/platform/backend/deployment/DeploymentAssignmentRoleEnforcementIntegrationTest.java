package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

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
class DeploymentAssignmentRoleEnforcementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private PlatformUserRepository platformUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void deploymentAssignmentRolesGateMutatingOperations() throws Exception {
        authenticateAdmin();
        DeploymentSummary deployment;
        DeploymentDraftResponse draft;
        try {
            deployment = deploymentService.createDeployment(
                new CreateDeploymentRequest("Role Enforcement", "dev", "dev-openai-lucene")
            );
            draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        } finally {
            SecurityContextHolder.clearContext();
        }

        createUser("usr-viewer-01", "viewer-role@example.com", "Viewer Role", "PLATFORM_OPERATOR", "ACTIVE", "ViewerPass123!");
        createUser("usr-editor-01", "editor-role@example.com", "Editor Role", "PLATFORM_OPERATOR", "ACTIVE", "EditorPass123!");
        createUser("usr-operator-01", "operator-role@example.com", "Operator Role", "PLATFORM_OPERATOR", "ACTIVE", "OperatorPass123!");

        Cookie adminSession = login("admin@example.com", "AdminPass123!");
        String viewerId = platformUserRepository.findByEmailIgnoreCase("viewer-role@example.com").orElseThrow().getId();
        String editorId = platformUserRepository.findByEmailIgnoreCase("editor-role@example.com").orElseThrow().getId();
        String operatorId = platformUserRepository.findByEmailIgnoreCase("operator-role@example.com").orElseThrow().getId();

        assign(adminSession, deployment.id(), viewerId, "DEPLOYMENT_VIEWER");
        assign(adminSession, deployment.id(), editorId, "DEPLOYMENT_EDITOR");
        assign(adminSession, deployment.id(), operatorId, "DEPLOYMENT_OPERATOR");

        Cookie viewerSession = login("viewer-role@example.com", "ViewerPass123!");
        Cookie editorSession = login("editor-role@example.com", "EditorPass123!");
        Cookie operatorSession = login("operator-role@example.com", "OperatorPass123!");

        mockMvc.perform(get("/api/deployments/{deploymentId}/workspace", deployment.id())
                .cookie(viewerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access.assignmentRole").value("DEPLOYMENT_VIEWER"))
            .andExpect(jsonPath("$.access.canOperate").value(false))
            .andExpect(jsonPath("$.access.canEdit").value(false))
            .andExpect(jsonPath("$.access.canAdmin").value(false));

        mockMvc.perform(get("/api/deployments/overview")
                .cookie(viewerSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(deployment.id()))
            .andExpect(jsonPath("$[0].access.assignmentRole").value("DEPLOYMENT_VIEWER"))
            .andExpect(jsonPath("$[0].access.canOperate").value(false))
            .andExpect(jsonPath("$[0].access.canEdit").value(false))
            .andExpect(jsonPath("$[0].access.canAdmin").value(false));

        mockMvc.perform(put("/api/deployment-drafts/{draftId}", draft.id())
                .cookie(viewerSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "promptConfig": {
                        "systemPrompt": "viewer update"
                      }
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/deployments/{deploymentId}/poc/prompt-session", deployment.id())
                .cookie(viewerSession))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/deployments/{deploymentId}/archive", deployment.id())
                .cookie(viewerSession))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/deployment-drafts/{draftId}", draft.id())
                .cookie(editorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "promptConfig": {
                        "systemPrompt": "editor update"
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(draft.id()));

        mockMvc.perform(post("/api/deployment-drafts/{draftId}/validate", draft.id())
                .cookie(editorSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.draftId").value(draft.id()));

        mockMvc.perform(post("/api/deployment-drafts/{draftId}/publish", draft.id())
                .cookie(editorSession))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.deploymentId").value(deployment.id()));

        mockMvc.perform(get("/api/deployments/overview")
                .cookie(editorSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].access.assignmentRole").value("DEPLOYMENT_EDITOR"))
            .andExpect(jsonPath("$[0].access.canOperate").value(true))
            .andExpect(jsonPath("$[0].access.canEdit").value(true))
            .andExpect(jsonPath("$[0].access.canAdmin").value(false));

        mockMvc.perform(post("/api/deployments/{deploymentId}/archive", deployment.id())
                .cookie(editorSession))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/deployments/{deploymentId}/poc/prompt-session", deployment.id())
                .cookie(operatorSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId").value(deployment.id()))
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(put("/api/deployment-drafts/{draftId}", draft.id())
                .cookie(operatorSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "promptConfig": {
                        "systemPrompt": "operator update"
                      }
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/deployments/{deploymentId}/archive", deployment.id())
                .cookie(operatorSession))
            .andExpect(status().isForbidden());
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
