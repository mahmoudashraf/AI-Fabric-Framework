package com.ai.fabric.runtime.web;

import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeScopeCatalog;
import com.ai.fabric.runtime.smartbrain.SmartBrainOperationService;
import com.ai.fabric.runtime.smartbrain.SmartBrainOperationView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SmartBrainControllerTest {

    private RuntimeRequestAuthResolver authResolver;
    private SmartBrainOperationService operationService;
    private RuntimeResolvedIdentity identity;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authResolver = mock(RuntimeRequestAuthResolver.class);
        operationService = mock(SmartBrainOperationService.class);
        identity = mock(RuntimeResolvedIdentity.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new SmartBrainController(authResolver, operationService)).build();
    }

    @Test
    void triggerRequiresPrivateRuntimeContextAndExactScope() throws Exception {
        when(authResolver.resolveVerifiedPrivateContext(any(), eq("/api/smart-brain/v1/triggers/{triggerCode}")))
            .thenReturn(identity);
        when(operationService.submit(any(), eq("risk-analysis"), any(String.class), eq("event-1")))
            .thenReturn(view());

        mockMvc.perform(post("/api/smart-brain/v1/triggers/risk-analysis")
                .header("Idempotency-Key", "event-1")
                .contentType("application/cloudevents+json")
                .content("""
                    {
                      "specversion":"1.0",
                      "id":"evt-1",
                      "source":"https://customer.example/events",
                      "type":"com.example.risk.requested",
                      "data":{"accountId":"acc-1"}
                    }
                    """))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.operationId").value("op-1"));

        verify(authResolver).resolveVerifiedPrivateContext(any(), eq("/api/smart-brain/v1/triggers/{triggerCode}"));
        verify(authResolver).requireScope(
            identity,
            RuntimeScopeCatalog.SMART_BRAIN_TRIGGER,
            "/api/smart-brain/v1/triggers/{triggerCode}"
        );
        verify(authResolver, never()).resolveVerifiedForChat(any());
    }

    private SmartBrainOperationView view() {
        Instant now = Instant.parse("2026-09-19T10:00:00Z");
        return new SmartBrainOperationView(
            "op-1",
            "risk-analysis",
            "evt-1",
            "com.example.risk.requested",
            "ACCEPTED",
            "https://runtime.example/api/smart-brain/v1/operations/op-1",
            null,
            null,
            now,
            now,
            null,
            now.plusSeconds(3600),
            false
        );
    }
}
