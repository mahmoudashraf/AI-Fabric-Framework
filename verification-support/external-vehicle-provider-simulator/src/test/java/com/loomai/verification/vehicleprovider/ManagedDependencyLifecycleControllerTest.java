package com.loomai.verification.vehicleprovider;

import com.loomai.verification.vehicleprovider.config.ManagedDependencyLifecycleController;
import com.loomai.verification.vehicleprovider.service.ManagedDependencyLifecycleService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ManagedDependencyLifecycleControllerTest {
    @Test void currentStateRequiresManagementAuthenticationAndDisablesCaching() throws Exception {
        var service = mock(ManagedDependencyLifecycleService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ManagedDependencyLifecycleController(service)).build();
        when(service.authorized("management-key")).thenReturn(true);
        var current = new ManagedDependencyLifecycleService.Readback(ManagedDependencyLifecycleService.VERSION,
            "READY", "customer", "tenant", "deployment", "target", "version", "simulator", "service", "account",
            "binding", "generation-2", 0, Instant.now(), "current-evidence", false);
        when(service.current("fresh-challenge-012345"))
            .thenReturn(new ManagedDependencyLifecycleService.CurrentReadback(current, "fresh-challenge-012345"));

        mvc.perform(get("/internal/loomai-managed-dependency/v1/lifecycle/status")
                .param("challenge", "fresh-challenge-012345"))
            .andExpect(status().isUnauthorized());
        verify(service, never()).current(anyString());
        mvc.perform(get("/internal/loomai-managed-dependency/v1/lifecycle/status")
                .header("X-LoomAI-Managed-Dependency-Key", "management-key")
                .param("challenge", "fresh-challenge-012345"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.current.operationKey").value("generation-2"))
            .andExpect(jsonPath("$.current.replay").value(false))
            .andExpect(jsonPath("$.challenge").value("fresh-challenge-012345"));
    }
}
