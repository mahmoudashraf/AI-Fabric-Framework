package com.ai.fabric.runtime.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.specialist.DeploymentKnowledgeQuestion;
import com.ai.fabric.runtime.specialist.DeploymentKnowledgeSpecialistService;
import com.ai.fabric.runtime.web.dto.DeploymentKnowledgeQueryResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DeploymentKnowledgeSpecialistControllerTest {

    private RuntimeRequestAuthResolver authResolver;
    private DeploymentKnowledgeSpecialistService specialistService;
    private RuntimeResolvedIdentity identity;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authResolver = mock(RuntimeRequestAuthResolver.class);
        specialistService = mock(
            DeploymentKnowledgeSpecialistService.class
        );
        identity = mock(RuntimeResolvedIdentity.class);
        DeploymentKnowledgeSpecialistController controller =
            new DeploymentKnowledgeSpecialistController(
                authResolver,
                specialistService
            );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new RuntimeControllerAdvice())
            .build();
    }

    @Test
    void resolvesPrivateIdentityAndRequiresBothExactScopes() throws Exception {
        when(authResolver.resolveVerifiedPrivateContext(
            any(),
            anyString()
        )).thenReturn(identity);
        when(specialistService.query(
            any(),
            any(DeploymentKnowledgeQuestion.class)
        )).thenReturn(new DeploymentKnowledgeSpecialistService.QueryResult(
            HttpStatus.OK,
            new DeploymentKnowledgeQueryResponse(
                "ANSWERED",
                "Approved answer",
                "deployment-knowledge-specialist",
                "1",
                "exec-123",
                List.of(),
                null
            )
        ));

        mockMvc.perform(post(
                "/api/specialists/deployment-knowledge/query"
            )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question":"Which provider is configured?"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ANSWERED"))
            .andExpect(jsonPath("$.correlationId").value("exec-123"));

        verify(authResolver).requireScope(
            identity,
            DeploymentKnowledgeSpecialistService.SPECIALIST_SCOPE,
            "deployment knowledge specialist"
        );
        verify(authResolver).requireScope(
            identity,
            DeploymentKnowledgeSpecialistService.VECTOR_SCOPE,
            "deployment knowledge specialist"
        );
        verify(specialistService).query(
            identity,
            new DeploymentKnowledgeQuestion(
                "Which provider is configured?"
            )
        );
    }

    @Test
    void rejectsCallerOwnedIdentityFieldsBeforeExecution() throws Exception {
        mockMvc.perform(post(
                "/api/specialists/deployment-knowledge/query"
            )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "question": "Which provider is configured?",
                      "tenantId": "caller-owned",
                      "deploymentId": "dep-other",
                      "scopes": ["specialist:*"]
                    }
                    """))
            .andExpect(status().isBadRequest());

        verify(authResolver, never()).resolveVerifiedPrivateContext(
            any(),
            anyString()
        );
        verify(specialistService, never()).query(any(), any());
    }

    @Test
    void rejectsBlankQuestionBeforeExecution() throws Exception {
        mockMvc.perform(post(
                "/api/specialists/deployment-knowledge/query"
            )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"question":"   "}
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));

        verify(specialistService, never()).query(any(), any());
    }
}
