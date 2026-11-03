package com.ai.fabric.platform.backend.support;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ai.fabric.platform.backend.support.runtime.SupportRuntimeAccessService;
import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SupportTicketControllerTest {
  @AfterEach
  void logout() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void requesterRoutesDelegateVersionAndIdempotencyAndOldCaseRouteIsAbsent() throws Exception {
    var f = new SupportFixture();
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new SupportTicketController(
                    f.service, f.conversations, mock(SupportRuntimeAccessService.class)))
            .build();
    var created =
        mvc.perform(
                post("/api/support/requests")
                    .header("Idempotency-Key", "submit")
                    .contentType("application/json")
                    .content("{\"subject\":\"Help\",\"details\":\"Setup\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andReturn();
    String id = f.json.readTree(created.getResponse().getContentAsString()).path("id").asText();
    mvc.perform(
            post("/api/support/requests/" + id + "/resolve")
                .header("Idempotency-Key", "resolve")
                .contentType("application/json")
                .content("{\"expectedVersion\":\"vendor-etag\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESOLVED"));
    f.login("bob");
    mvc.perform(get("/api/support/requests/" + id)).andExpect(status().isNotFound());
    mvc.perform(get("/api/support/cases/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void intakeAndCommandKeyAreRequiredWithoutProviderWrites() throws Exception {
    var f = new SupportFixture();
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new SupportTicketController(
                    f.service, f.conversations, mock(SupportRuntimeAccessService.class)))
            .build();
    mvc.perform(
            post("/api/support/requests")
                .contentType("application/json")
                .content("{\"subject\":\"x\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/support/requests")
                .header("Idempotency-Key", "submit")
                .contentType("application/json")
                .content("{\"subject\":\"\"}"))
        .andExpect(status().isBadRequest());
    verify(f.ticketClient, never()).request(any(), anyString(), anyString(), any(), any());
  }
}
