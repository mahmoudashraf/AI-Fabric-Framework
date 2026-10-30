package com.ai.fabric.platform.backend.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SupportCaseControllerTest {
    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void customerRoutesUseRequesterScopedServiceAndDoNotExposeAdapterAuthority() throws Exception {
        var f = new SupportFixture();
        var mvc = MockMvcBuilders.standaloneSetup(new SupportCaseController(f.service)).build();
        var response = mvc.perform(post("/api/support/cases").header("Idempotency-Key", "form-submit")
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"summary":"Help me connect my app","details":"I need setup guidance","diagnosticConsent":false}
                """))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.principalId").doesNotExist()).andExpect(jsonPath("$.runtimePrincipal").doesNotExist())
            .andExpect(jsonPath("$.initialMessageKey").doesNotExist()).andExpect(jsonPath("$.requester").doesNotExist())
            .andReturn();
        var id = f.json.readTree(response.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(get("/api/support/cases/" + id)).andExpect(status().isOk());
        mvc.perform(post("/api/support/cases/" + id + "/resolve").contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        f.login("bob");
        mvc.perform(get("/api/support/cases/" + id)).andExpect(status().isNotFound());
    }

    @Test void requiredIntakeAndIdempotencyAreValidatedWithoutCreatingCases() throws Exception {
        var f = new SupportFixture(); var mvc = MockMvcBuilders.standaloneSetup(new SupportCaseController(f.service)).build();
        mvc.perform(post("/api/support/cases").contentType(MediaType.APPLICATION_JSON).content("{\"summary\":\"x\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/support/cases").header("Idempotency-Key", "submit")
            .contentType(MediaType.APPLICATION_JSON).content("{\"summary\":\"\"}"))
            .andExpect(status().isBadRequest());
    }
}
