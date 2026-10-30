package com.ai.fabric.platform.backend.support.runtime;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SupportWorkspaceControllerTest {
    @Test void canonicalRoutesPreserveTheCaseScopeAndNoStoreProjection() throws Exception {
        var access = mock(SupportRuntimeAccessService.class);
        var service = mock(SupportWorkspaceService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new SupportWorkspaceController(access, service)).build();
        when(service.exchange(eq("sup-1"), eq("POST"), eq("chat/me/conversations/chat-1/messages"), any(), eq("key"), eq(0L)))
            .thenReturn(new SupportRuntimeClient.Result(200, new ObjectMapper().readTree("{\"success\":true}")));
        mvc.perform(post("/api/support/cases/sup-1/runtime/api/chat/me/conversations/chat-1/messages")
            .header("Idempotency-Key", "key").contentType("application/json").content("{\"query\":\"Help\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.success").value(true));
    }
}
