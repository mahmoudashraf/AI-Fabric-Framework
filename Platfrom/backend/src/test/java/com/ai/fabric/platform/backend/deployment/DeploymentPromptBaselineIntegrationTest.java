package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
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
class DeploymentPromptBaselineIntegrationTest {

    private static final String PLATFORM_API_KEY_HEADER = "X-PLATFORM-API-KEY";
    private static final String ADMIN_API_KEY = "admin-test-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void publicationPersistsCompiledKnowledgeGuidanceWithoutMutatingTheNextEditablePrompt() throws Exception {
        var deployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Published knowledge guidance", "dev", "dev-openai-lucene", "default")
        ));
        var draft = runAsAdmin(() -> deploymentService.getActiveDraftForDeployment(deployment.id()));
        var prompts = objectMapper.readTree("{\"systemPrompt\":\"Use our voice\",\"intentExtractionPrompt\":\"Preserve constraints\"}");
        var knowledge = objectMapper.readTree("""
            {"contractVersion":"KNOWLEDGE_SOURCE_CONFIG_V1","sources":[{
              "id":"approved-catalogue","type":"deployment-private-vector",
              "adapterType":"deployment-private-vector","entityType":"document",
              "attributionLabel":"Approved company knowledge","enabled":true
            }]}
            """);
        runAsAdmin(() -> deploymentService.updateDraft(draft.id(),
            new UpdateDeploymentDraftRequest(null, null, null, null, null, prompts, knowledge, null)));

        runAsAdmin(() -> deploymentService.publishDraft(draft.id()));

        mockMvc.perform(get("/api/deployments/{deploymentId}/prompt-baseline", deployment.id())
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.promptConfig.systemPrompt", containsString("Use our voice")))
            .andExpect(jsonPath("$.promptConfig.systemPrompt", containsString("Approved company knowledge")))
            .andExpect(jsonPath("$.promptConfig.intentExtractionPrompt", containsString("obligationId")));
        var nextDraft = runAsAdmin(() -> deploymentService.getActiveDraftForDeployment(deployment.id()));
        assertThat(nextDraft.promptConfig()).isEqualTo(prompts);
    }

    @Test
    void promptBaselineReturnsLatestPublishedPromptBundle() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Prompt Baseline", "dev", "dev-openai-lucene", "commerce")
        ));

        mockMvc.perform(get("/api/deployments/{deploymentId}/prompt-baseline", deployment.id())
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deployment.id())))
            .andExpect(jsonPath("$.versionId", nullValue()))
            .andExpect(jsonPath("$.populatedPromptCount", is(0)));

        DeploymentDraftResponse draft = runAsAdmin(() -> deploymentService.getActiveDraftForDeployment(deployment.id()));
        runAsAdmin(() -> deploymentService.publishDraft(draft.id()));

        mockMvc.perform(get("/api/deployments/{deploymentId}/prompt-baseline", deployment.id())
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deployment.id())))
            .andExpect(jsonPath("$.versionId", notNullValue()))
            .andExpect(jsonPath("$.publishedAt", notNullValue()))
            .andExpect(jsonPath("$.populatedPromptCount", is(7)))
            .andExpect(jsonPath("$.promptConfig.systemPrompt", is("You are a commerce assistant for customer-facing and operator-assisted workflows. Ground answers in live products, orders, reviews, and policy data whenever available. Never invent fulfillment, refund, or pricing outcomes.")));
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
