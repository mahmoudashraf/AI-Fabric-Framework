package com.ai.fabric.platform.backend.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SupportDeskContextControllerTest {
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void lookupAndResolveShareOneEndpointAndStaffListRequiresActualAgentBearer() throws Exception {
        var f = new SupportDeskFixture(); var c = f.create(false); var lookup = f.lookup(c.id());
        var mvc = MockMvcBuilders.standaloneSetup(new SupportDeskContextController(f.contexts)).build();
        SecurityContextHolder.clearContext();
        mvc.perform(post("/api/integrations/support/desk/context").headers(f.deskHeaders())
            .contentType(MediaType.APPLICATION_JSON).content(f.json.writeValueAsBytes(lookup)))
            .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.state").value("AVAILABLE"))
            .andExpect(jsonPath("$.scope.conversationId").value(lookup.scope().conversationId()))
            .andExpect(jsonPath("$.context.reference").value(c.reference()));
        mvc.perform(get("/api/support/staff/intake").header(HttpHeaders.AUTHORIZATION, "Bearer actual-agent-token"))
            .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$[0].caseId").value(c.id())).andExpect(jsonPath("$[0].principalId").doesNotExist());
        mvc.perform(post("/api/integrations/support/desk/context").headers(f.deskHeaders())
            .contentType(MediaType.APPLICATION_JSON).content(f.json.writeValueAsBytes(f.resolve(lookup, 0, "resolve-api"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.context.status").value("RESOLVED"))
            .andExpect(jsonPath("$.context.resolveAction").isEmpty());
        mvc.perform(get("/api/support/staff/intake")).andExpect(status().isForbidden());
    }
}
