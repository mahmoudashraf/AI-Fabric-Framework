package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
class DeploymentPromptRevisionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeploymentService deploymentService;

    @Test
    void promptRevisionCanBeCreatedAndRestored() throws Exception {
        authenticateAdmin();
        DeploymentSummary deployment;
        DeploymentDraftResponse draft;
        try {
            deployment = deploymentService.createDeployment(
                new CreateDeploymentRequest("Prompt Bundle Smoke", "dev", "dev-openai-lucene")
            );
            draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        } finally {
            SecurityContextHolder.clearContext();
        }
        Cookie adminSession = login("admin@example.com", "AdminPass123!");

        mockMvc.perform(put("/api/deployment-drafts/{draftId}", draft.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "promptConfig": {
                            "systemPrompt": "You are a commerce assistant focused on grounded answers.",
                            "answerGenerationPrompt": "Answer with short, precise summaries."
                          }
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.promptConfig.systemPrompt", is("You are a commerce assistant focused on grounded answers.")))
            .andExpect(jsonPath("$.promptConfig.answerGenerationPrompt", is("Answer with short, precise summaries.")));

        String revisionResponse = mockMvc.perform(post("/api/deployments/{deploymentId}/prompt-revisions", deployment.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "revisionLabel": "Grounded commerce baseline",
                          "revisionSummary": "System and answer prompts tuned for commerce rollout."
                        }
                        """
                ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.revisionLabel", is("Grounded commerce baseline")))
            .andExpect(jsonPath("$.populatedPromptCount", is(2)))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String revisionId = objectMapper.readTree(revisionResponse).path("id").asText();

        mockMvc.perform(get("/api/deployments/{deploymentId}/prompt-revisions", deployment.id())
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='%s')].revisionLabel".formatted(revisionId), hasItem("Grounded commerce baseline")));

        mockMvc.perform(put("/api/deployment-drafts/{draftId}", draft.id())
                .cookie(adminSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "promptConfig": {
                            "systemPrompt": "",
                            "answerGenerationPrompt": ""
                          }
                        }
                        """
                ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.promptConfig.systemPrompt", is("")));

        mockMvc.perform(post("/api/deployments/{deploymentId}/prompt-revisions/{revisionId}/restore", deployment.id(), revisionId)
                .cookie(adminSession))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.promptConfig.systemPrompt", is("You are a commerce assistant focused on grounded answers.")))
            .andExpect(jsonPath("$.promptConfig.answerGenerationPrompt", is("Answer with short, precise summaries.")));
    }

    private Cookie login(String email, String password) throws Exception {
        String cookieHeader = mockMvc.perform(post("/api/platform/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                        {
                          "email": "%s",
                          "password": "%s"
                        }
                        """.formatted(email, password)
                ))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getHeader("Set-Cookie");

        String sessionValue = cookieHeader
            .substring(0, cookieHeader.indexOf(';'))
            .replace("platform_session=", "");
        return new Cookie("platform_session", sessionValue);
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
