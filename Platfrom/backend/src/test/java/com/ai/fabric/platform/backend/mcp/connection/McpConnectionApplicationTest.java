package com.ai.fabric.platform.backend.mcp.connection;

import com.ai.fabric.platform.backend.mcp.runtime.*;
import com.ai.fabric.platform.backend.security.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real application graph, migration/validation and security chain; never calls an external MCP or model. */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:platform-mcp-application;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.flyway.target=120.35", "platform.bootstrap.sample-enabled=false", "platform.auth.bootstrap-admin-enabled=false",
    "platform.auth.enabled=true", "platform.mcp.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "platform.mcp.callback-url=https://api.loomai.example/api/mcp/oauth/callback",
    "platform.mcp.console-base-url=https://console.loomai.example",
    "platform.mcp.client-metadata-url=https://api.loomai.example/api/mcp/oauth/client-metadata"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class McpConnectionApplicationTest {
    @Autowired MockMvc mvc;
    @Autowired McpConnectionService service;
    @Autowired McpBusinessOAuthService oauth;
    @Autowired McpConnectionReadiness readiness;
    @Autowired McpRuntimeConnectionResolver runtime;
    @Autowired McpConnectionRepository connections;
    @Autowired McpDiscoverySnapshotRepository snapshots;
    @Autowired McpImportReceiptRepository receipts;
    @Autowired McpOAuthAttemptRepository attempts;

    @Test void fullBeansAndAllDurableMcpTablesStartWithoutCircularDependencies() {
        assertThat(service).isNotNull(); assertThat(oauth).isNotNull(); assertThat(readiness).isNotNull(); assertThat(runtime).isNotNull();
        assertThat(connections.count()).isZero(); assertThat(snapshots.count()).isZero();
        assertThat(receipts.count()).isZero(); assertThat(attempts.count()).isZero();
    }

    @Test void anonymousAndPublicClientCannotUseCustomerConnectionConfiguration() throws Exception {
        String base = "/api/deployments/unknown-brain/mcp-connections";
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(get(base).with(authentication(caller(PlatformRole.PUBLIC_API_CLIENT)))).andExpect(status().isForbidden());
        mvc.perform(post(base + "/unknown-connection/discover").with(authentication(caller(PlatformRole.PUBLIC_API_CLIENT))))
            .andExpect(status().isForbidden());
        for (var role : List.of(PlatformRole.CUSTOMER_ADMIN, PlatformRole.PLATFORM_OPERATOR, PlatformRole.PLATFORM_ADMIN))
            mvc.perform(get(base).with(authentication(caller(role)))).andExpect(status().isNotFound());
        assertThat(connections.count()).isZero();
    }

    @Test void browserConsoleRoleCannotReplaceTheRuntimeTargetKey() throws Exception {
        String endpoint = "/api/mcp/runtime/unknown-brain/unknown-target/actions/execute";
        mvc.perform(post(endpoint).contentType("application/json").content("{\"actionId\":\"cancel\",\"params\":{}}"))
            .andExpect(status().isForbidden());
        mvc.perform(post(endpoint).with(authentication(caller(PlatformRole.PLATFORM_ADMIN))).contentType("application/json")
            .content("{\"actionId\":\"cancel\",\"params\":{}}"))
            .andExpect(status().isForbidden());
        mvc.perform(post(endpoint).header("X-LoomAI-MCP-Runtime-Key", "wrong-target-key").contentType("application/json")
            .content("{\"actionId\":\"cancel\",\"params\":{}}"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("MCP_ACCESS_DENIED"));
    }

    @Test void businessOAuthMetadataIsPublicButAnUnsolicitedCallbackGrantsNothing() throws Exception {
        mvc.perform(get("/api/mcp/oauth/client-metadata")).andExpect(status().isOk())
            .andExpect(jsonPath("$.redirect_uris[0]").value("https://api.loomai.example/api/mcp/oauth/callback"))
            .andExpect(jsonPath("$.token_endpoint_auth_method").value("none"));
        mvc.perform(get("/api/mcp/oauth/callback").param("state", "unsolicited-state").param("code", "fake-code"))
            .andExpect(status().isBadRequest());
        assertThat(attempts.count()).isZero(); assertThat(connections.count()).isZero();
    }

    private static UsernamePasswordAuthenticationToken caller(PlatformRole role) {
        return new UsernamePasswordAuthenticationToken(new PlatformPrincipal("fixture-actor", role, "Fixture", "TEST"), null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
    }
}
