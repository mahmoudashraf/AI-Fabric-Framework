package com.ai.fabric.platform.backend.mcp.connection;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class McpConnectionControllerTest {
    @Test void authorizeSetsSecureHttpOnlyBrowserBindingWithoutReturningItAsJson() throws Exception {
        var connections = mock(McpConnectionService.class); var oauth = mock(McpBusinessOAuthService.class);
        when(oauth.authorize(eq("dep-one"), eq("mcp-one"), any(), anyString())).thenReturn(
            new McpConnectionContracts.AuthorizationStart("https://auth.example/authorize?state=test-state", Instant.now().plusSeconds(600), "mcp-one"));
        var mvc = MockMvcBuilders.standaloneSetup(new McpConnectionController(connections, oauth)).build();
        var result = mvc.perform(post("/api/deployments/dep-one/mcp-connections/mcp-one/authorize").contentType("application/json")
            .content("{\"expectedRevision\":1,\"returnPath\":\"/smart-brains/dep-one/capabilities\"}"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.authorizationUrl").value("https://auth.example/authorize?state=test-state")).andReturn();
        String cookie = result.getResponse().getHeader("Set-Cookie");
        assertThat(cookie).contains("loomai_mcp_oauth=", "Secure", "HttpOnly", "SameSite=Lax", "Path=/api/mcp/oauth");
        var binding = ArgumentCaptor.forClass(String.class); verify(oauth).authorize(eq("dep-one"), eq("mcp-one"), any(), binding.capture());
        assertThat(cookie).contains(binding.getValue()); assertThat(result.getResponse().getContentAsString()).doesNotContain(binding.getValue());
    }

    @Test void storedSnapshotRouteReadsOwnedMetadataWithoutAnotherDiscoveryCall() throws Exception {
        var connections = mock(McpConnectionService.class); var oauth = mock(McpBusinessOAuthService.class);
        when(connections.getSnapshot("dep-one", "mcp-one", "snapshot-one")).thenReturn(new McpConnectionContracts.Snapshot(
            "snapshot-one", "mcp-one", 1, Instant.now(), true, "2025-11-25", List.of(), "Complete"));
        var mvc = MockMvcBuilders.standaloneSetup(new McpConnectionController(connections, oauth)).build();
        mvc.perform(get("/api/deployments/dep-one/mcp-connections/mcp-one/snapshots/snapshot-one"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value("snapshot-one")).andExpect(header().string("Cache-Control", "no-store"));
        verify(connections, never()).discover(anyString(), anyString()); verifyNoInteractions(oauth);
    }

    @Test void callbackRedirectsToTrustedConsoleOriginWithoutCodeOrState() throws Exception {
        var oauth = mock(McpBusinessOAuthService.class); var properties = new McpConnectionProperties();
        properties.setConsoleBaseUrl("https://console.loomai.example");
        when(oauth.callback(eq("state"), eq("secret-code"), isNull(), isNull(), eq("browser"))).thenReturn(
            new McpConnectionContracts.CallbackResult("/smart-brains/dep-one/capabilities?view=mcp", "connected"));
        var mvc = MockMvcBuilders.standaloneSetup(new McpOAuthCallbackController(oauth, properties, new ObjectMapper())).build();
        var result = mvc.perform(get("/api/mcp/oauth/callback").param("state", "state").param("code", "secret-code")
            .cookie(new jakarta.servlet.http.Cookie(McpOAuthCallbackController.COOKIE_NAME, "browser")))
            .andExpect(status().isSeeOther()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Referrer-Policy", "no-referrer")).andReturn();
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("https://console.loomai.example/smart-brains/dep-one/capabilities?view=mcp&mcpOAuth=connected");
        assertThat(result.getResponse().getHeader("Location")).doesNotContain("secret-code", "state=");
        assertThat(result.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0", "HttpOnly");
    }
}
