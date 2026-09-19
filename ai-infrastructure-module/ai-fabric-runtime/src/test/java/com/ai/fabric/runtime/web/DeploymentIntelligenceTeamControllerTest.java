package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.agentic.DeploymentIntelligenceExecutionView;
import com.ai.fabric.runtime.agentic.DeploymentIntelligenceTeamService;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeploymentIntelligenceTeamControllerTest {

    private RuntimeRequestAuthResolver authResolver;
    private DeploymentIntelligenceTeamService teamService;
    private RuntimeResolvedIdentity identity;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authResolver = mock(RuntimeRequestAuthResolver.class);
        teamService = mock(DeploymentIntelligenceTeamService.class);
        identity = mock(RuntimeResolvedIdentity.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
            new DeploymentIntelligenceTeamController(authResolver, teamService)
        ).build();
    }

    @Test
    void asynchronousSubmissionUsesVerifiedInteractiveOrBackendIdentityAndExactScope() throws Exception {
        when(authResolver.resolveVerifiedForChat(any())).thenReturn(identity);
        when(teamService.submit(any(), any(), eq("agentic-1"))).thenReturn(view());

        mockMvc.perform(post("/api/agentic/v1/executions")
                .header("Idempotency-Key", "agentic-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"Compare indexed deployment evidence with current runtime state\"}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.executionId").value("chain-execution-1"))
            .andExpect(jsonPath("$.chain").value("deployment-intelligence-team@1"));

        verify(authResolver).resolveVerifiedForChat(any());
        verify(authResolver).requireScope(
            identity,
            RuntimeScopeCatalog.AGENTIC_TEAM_EXECUTE,
            "/api/agentic/v1/executions"
        );
    }

    private DeploymentIntelligenceExecutionView view() {
        Instant now = Instant.parse("2026-09-19T10:00:00Z");
        return new DeploymentIntelligenceExecutionView(
            "chain-execution-1",
            "deployment-intelligence-team@1",
            "ACCEPTED",
            false,
            true,
            null,
            null,
            List.of(),
            List.of(),
            now,
            null,
            now,
            null,
            now.plusSeconds(75)
        );
    }
}
