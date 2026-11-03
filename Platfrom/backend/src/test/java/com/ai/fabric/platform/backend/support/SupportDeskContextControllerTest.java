package com.ai.fabric.platform.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SupportDeskContextControllerTest {
  @AfterEach
  void logout() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void lookupIsReadOnlyAndOldStaffIntakeIsNotRegistered() throws Exception {
    var f = new SupportDeskFixture();
    var saved = f.create(false);
    var lookup = f.lookup(saved.id());
    var mvc = MockMvcBuilders.standaloneSetup(new SupportDeskContextController(f.contexts)).build();
    SecurityContextHolder.clearContext();
    mvc.perform(
            post("/api/integrations/support/desk/context")
                .headers(f.deskHeaders())
                .contentType("application/json")
                .content(f.json.writeValueAsBytes(lookup)))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.context.reference").value(saved.reference()))
        .andExpect(jsonPath("$.context.resolveAction").isEmpty());
    mvc.perform(
            post("/api/integrations/support/desk/context")
                .headers(f.deskHeaders())
                .contentType("application/json")
                .content(f.json.writeValueAsBytes(f.resolve(lookup, 0, "retired"))))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/support/staff/intake")).andExpect(status().isNotFound());
  }
}
