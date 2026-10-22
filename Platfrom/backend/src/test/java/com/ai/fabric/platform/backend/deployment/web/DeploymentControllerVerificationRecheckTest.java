package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRunSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeploymentControllerVerificationRecheckTest {
    private final DeploymentService service = mock(DeploymentService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var controller = new DeploymentController(service, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void optionalQuerySelectsExactReleaseAndRetainsCreatedResponse() throws Exception {
        when(service.rerunVerification("dep-1", "rel-staging")).thenReturn(summary("rel-staging"));

        mvc.perform(post("/api/deployments/dep-1/verification-runs/recheck").param("releaseId", "rel-staging"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.releaseId").value("rel-staging"))
            .andExpect(jsonPath("$.deploymentVersionId").value("ver-1"))
            .andExpect(jsonPath("$.verificationType").value("MANUAL_RERUN"));

        verify(service).rerunVerification("dep-1", "rel-staging");
        verifyNoMoreInteractions(service);
    }

    @Test
    void omittedQueryUsesLegacyServiceSelection() throws Exception {
        when(service.rerunVerification("dep-1", null)).thenReturn(summary("rel-latest"));

        mvc.perform(post("/api/deployments/dep-1/verification-runs/recheck"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.releaseId").value("rel-latest"));

        verify(service).rerunVerification("dep-1", null);
        verifyNoMoreInteractions(service);
    }

    @Test
    void explicitlyEmptyQueryIsNotConvertedIntoAnOmittedRelease() throws Exception {
        when(service.rerunVerification("dep-1", "")).thenThrow(
            new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST));

        mvc.perform(post("/api/deployments/dep-1/verification-runs/recheck").param("releaseId", ""))
            .andExpect(status().isBadRequest());

        verify(service).rerunVerification("dep-1", "");
        verifyNoMoreInteractions(service);
    }

    private DeploymentVerificationRunSummary summary(String releaseId) {
        return new DeploymentVerificationRunSummary("vrf-1", "dep-1", releaseId, "ver-1", "MANUAL_RERUN",
            "PASSED", "Verified", new ObjectMapper().createArrayNode(), null, null);
    }
}
