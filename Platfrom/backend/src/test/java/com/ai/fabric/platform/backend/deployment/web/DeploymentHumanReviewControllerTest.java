package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.SubmitDeploymentHumanReviewDecisionRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentHumanReviewService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeploymentHumanReviewControllerTest {

    private final DeploymentHumanReviewService service = mock(DeploymentHumanReviewService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new DeploymentHumanReviewController(service)).build();
    }

    @Test
    void allRoutesRequireAnExplicitTarget() throws Exception {
        mvc.perform(get("/api/deployments/dep-1/human-reviews")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/deployments/dep-1/human-reviews/task-1")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/deployments/dep-1/human-reviews/task-1/decisions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"decision\":\"APPROVE\",\"expectedVersion\":3,\"decisionId\":\"decision-1\"}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void inboxForwardsSelectedTargetAndLimit() throws Exception {
        when(service.inbox("dep-1", "production", 25))
            .thenReturn(new DeploymentHumanReviewService.RuntimeReviewResponse(200, mapper.createArrayNode()));

        mvc.perform(get("/api/deployments/dep-1/human-reviews")
            .param("targetProfileId", "production").param("limit", "25")).andExpect(status().isOk());

        verify(service).inbox("dep-1", "production", 25);
    }

    @Test
    void detailForwardsSelectedTarget() throws Exception {
        when(service.detail("dep-1", "staging", "task-1"))
            .thenReturn(new DeploymentHumanReviewService.RuntimeReviewResponse(200, mapper.createObjectNode()));

        mvc.perform(get("/api/deployments/dep-1/human-reviews/task-1")
            .param("targetProfileId", "staging")).andExpect(status().isOk());

        verify(service).detail("dep-1", "staging", "task-1");
    }

    @Test
    void decisionForwardsSelectedTargetWithoutChangingDecisionContract() throws Exception {
        var decision = new SubmitDeploymentHumanReviewDecisionRequest("APPROVE", 3, "decision-1");
        when(service.decide("dep-1", "production", "task-1", decision))
            .thenReturn(new DeploymentHumanReviewService.RuntimeReviewResponse(200, mapper.createObjectNode()));

        mvc.perform(post("/api/deployments/dep-1/human-reviews/task-1/decisions")
            .param("targetProfileId", "production")
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(decision)))
            .andExpect(status().isOk());

        verify(service).decide("dep-1", "production", "task-1", decision);
    }
}
