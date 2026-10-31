package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SupportTeamControllerTest {
    private static final String REF = "team-" + "a".repeat(64);
    private static final String PATH = "/api/desk/admin/v1/bindings/support-binding/support-team";
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0, 1};

    @AfterEach void logout() { SecurityContextHolder.clearContext(); }

    @Test void requesterProjectionUsesOnlyConfiguredBindingAndNeverForwardsPrivateProfileFields() throws Exception {
        var f = new SupportDeskFixture(); var mvc = mvc(f);
        when(f.desk.get(f.connection, PATH)).thenReturn(f.json.readTree("""
            [{"profileRef":"%s","displayName":"Sam Support","photoAvailable":true,
              "email":"private@example.test","subject":"hidden-subject","roles":["OWNER"],
              "avatarUrl":"/api/desk/v1/agents/private-id/photo","online":true}]
            """.formatted(REF)));
        when(f.desk.getPhoto(f.connection, PATH + "/" + REF + "/photo")).thenReturn(JPEG);
        var response = mvc.perform(get("/api/support/team").param("bindingId", "foreign-binding")
            .param("environment", "production"))
            .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
            .andExpect(jsonPath("$.agents[0].agentReference").value(REF))
            .andExpect(jsonPath("$.agents[0].displayName").value("Sam Support"))
            .andExpect(jsonPath("$.agents[0].photoAvailable").value(true)).andReturn().getResponse();
        assertThat(response.getContentAsString()).doesNotContain("email", "subject", "roles", "avatarUrl", "online", "private-id", "foreign-binding");
        mvc.perform(get("/api/support/team/" + REF + "/photo"))
            .andExpect(status().isOk()).andExpect(content().bytes(JPEG))
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        // Another active customer sees the same shared support team, not their own deployment's staff.
        f.login("eve");
        mvc.perform(get("/api/support/team")).andExpect(status().isOk());
        verify(f.desk, times(2)).get(f.connection, PATH);
        verify(f.desk, times(3)).connection("staging");
        verify(f.desk, never()).connection("production");
    }

    @Test void bothReadsReauthorizeMembershipAndIdentityLinkWithoutAStaleRosterGrant() throws Exception {
        var f = new SupportDeskFixture(); var mvc = mvc(f);
        when(f.desk.get(f.connection, PATH)).thenReturn(f.json.createArrayNode());
        mvc.perform(get("/api/support/team")).andExpect(status().isOk());
        clearInvocations(f.desk);
        f.directory.get("alice").setStatus("DISABLED");
        denied(mvc);
        f.directory.get("alice").setStatus("ACTIVE");
        f.identityLinks.get("alice").setRevokedAt(Instant.now());
        denied(mvc);
        verifyNoInteractions(f.desk);
    }

    @Test void anonymousApiKeyAndStaffRolesCannotReadCustomerProjection() throws Exception {
        var f = new SupportDeskFixture(); var mvc = mvc(f);
        SecurityContextHolder.clearContext(); denied(mvc);
        for (var principal : List.of(
            new PlatformPrincipal("alice@example.test", PlatformRole.CUSTOMER_ADMIN, "Alice", "API_KEY", "alice"),
            new PlatformPrincipal("admin@example.test", PlatformRole.PLATFORM_ADMIN, "Admin", "SESSION", "admin"),
            new PlatformPrincipal("operator@example.test", PlatformRole.PLATFORM_OPERATOR, "Operator", "SESSION", "operator"))) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
            denied(mvc);
        }
        verifyNoInteractions(f.desk);
    }

    @Test void supportBindingOwnershipEnabledStateAndTargetAreServerChecked() throws Exception {
        var f = new SupportDeskFixture(); var mvc = mvc(f);
        f.binding.setCustomerId("another-customer"); unavailable(mvc);
        f.binding.setCustomerId("internal-owner");
        f.binding.setTenantId("another-tenant"); unavailable(mvc);
        f.binding.setTenantId("internal-tenant");
        f.binding.setDeploymentId("another-deployment"); unavailable(mvc);
        f.binding.setDeploymentId("support-runtime");
        f.binding.setTargetProfileId("another-target"); unavailable(mvc);
        f.binding.setTargetProfileId("staging-target");
        f.binding.setStatus("APPLYING"); unavailable(mvc);
        f.binding.setStatus("ENABLED");
        f.targets.findById("staging-target").orElseThrow().setActive(false); unavailable(mvc);
        f.targets.findById("staging-target").orElseThrow().setActive(true);
        f.env.setProperty("platform.support.enabled", "false"); unavailable(mvc);
        verifyNoInteractions(f.desk);
    }

    @Test void emptyRosterMissingPhotosAndMalformedUpstreamResponsesStayExplicit() throws Exception {
        var f = new SupportDeskFixture(); var mvc = mvc(f);
        when(f.desk.get(f.connection, PATH)).thenReturn(f.json.createArrayNode());
        mvc.perform(get("/api/support/team")).andExpect(status().isOk()).andExpect(jsonPath("$.agents").isEmpty());
        when(f.desk.get(f.connection, PATH)).thenReturn(f.json.readTree("""
            [{"profileRef":"%s","displayName":"private@example.test","photoAvailable":false}]
            """.formatted(REF)));
        mvc.perform(get("/api/support/team")).andExpect(status().isOk())
            .andExpect(jsonPath("$.agents[0].displayName").value("Support agent"));
        for (String invalid : List.of("{}", "[{\"profileRef\":\"private-id\",\"displayName\":\"Sam\",\"photoAvailable\":true}]",
            "[{\"profileRef\":\"" + REF + "\",\"displayName\":\"Sam\",\"photoAvailable\":\"yes\"}]")) {
            when(f.desk.get(f.connection, PATH)).thenReturn(f.json.readTree(invalid));
            mvc.perform(get("/api/support/team")).andExpect(status().isBadGateway())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));
        }
        mvc.perform(get("/api/support/team/private-id/photo")).andExpect(status().isNotFound());
        verify(f.desk, never()).getPhoto(any(), anyString());
        when(f.desk.getPhoto(f.connection, PATH + "/" + REF + "/photo"))
            .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND));
        mvc.perform(get("/api/support/team/" + REF + "/photo")).andExpect(status().isNotFound())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));
    }

    private MockMvc mvc(SupportDeskFixture f) {
        var service = new SupportTeamService(f.requesters, f.settings, f.deskBindings, f.targets, f.desk);
        return MockMvcBuilders.standaloneSetup(new SupportTeamController(service)).build();
    }
    private void denied(MockMvc mvc) throws Exception {
        mvc.perform(get("/api/support/team")).andExpect(status().isForbidden());
        mvc.perform(get("/api/support/team/" + REF + "/photo")).andExpect(status().isForbidden());
    }
    private void unavailable(MockMvc mvc) throws Exception {
        mvc.perform(get("/api/support/team")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/support/team/" + REF + "/photo")).andExpect(status().isServiceUnavailable());
    }
}
