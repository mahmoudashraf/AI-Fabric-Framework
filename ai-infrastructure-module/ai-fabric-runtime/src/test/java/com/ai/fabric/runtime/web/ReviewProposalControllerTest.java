package com.ai.fabric.runtime.web;

import ai.fabric.execution.review.ReviewTaskCreationResult;
import ai.fabric.execution.review.decision.ReviewDecisionFailure;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import com.ai.fabric.runtime.review.ReviewProposalConfiguration;
import com.ai.fabric.runtime.review.ReviewProposalService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReviewProposalControllerTest {
    private final RuntimeRequestAuthResolver auth = mock(RuntimeRequestAuthResolver.class);
    private final ReviewProposalService service = mock(ReviewProposalService.class);
    private final RuntimeResolvedIdentity identity = RuntimeResolvedIdentity.builder().build();
    private final org.springframework.test.web.servlet.MockMvc mvc = MockMvcBuilders
        .standaloneSetup(new ReviewProposalController(auth, service)).build();
    private final String body = """
        {"request":"Prepare the requested action.","title":"Application request","summary":"Safe summary."}
        """;

    @Test
    void requiresPrivateIdentityAndBothExactScopes() throws Exception {
        when(auth.resolveVerifiedPrivateContext(any(), anyString())).thenReturn(identity);
        when(service.propose(eq(identity), any(), eq("opaque-key"))).thenReturn(
            new ReviewTaskCreationResult(null, false,
                new ReviewDecisionFailure("ACTION_PROPOSAL_NOT_CREATED", "No governed proposal was produced.", false)));
        mvc.perform(post("/api/reviews/v1/application-proposals")
                .header("Idempotency-Key", "opaque-key").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.failure.reason").value("ACTION_PROPOSAL_NOT_CREATED"));
        verify(auth).requireScope(identity, RuntimeScopeCatalog.REVIEW_ACTION_PROPOSAL_CREATE, "application-proposals");
        verify(auth).requireScope(identity, ReviewProposalConfiguration.SPECIALIST_SCOPE, "application-proposals");
    }

    @Test
    void neverRunsProviderWorkForAnUnverifiedRequest() throws Exception {
        when(auth.resolveVerifiedPrivateContext(any(), anyString()))
            .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        mvc.perform(post("/api/reviews/v1/application-proposals").header("Idempotency-Key", "key")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void reviewerPermissionDoesNotGrantProposalAuthority() throws Exception {
        when(auth.resolveVerifiedPrivateContext(any(), anyString())).thenReturn(identity);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(auth)
            .requireScope(identity, RuntimeScopeCatalog.REVIEW_ACTION_PROPOSAL_CREATE, "application-proposals");
        mvc.perform(post("/api/reviews/v1/application-proposals").header("Idempotency-Key", "key")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
}
