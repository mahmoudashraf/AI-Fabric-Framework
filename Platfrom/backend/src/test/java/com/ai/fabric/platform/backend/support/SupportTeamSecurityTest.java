package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.security.service.CustomerSupabaseUserVerifier;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SupportTeamSecurityTest {
    private AnnotationConfigWebApplicationContext web;

    @AfterEach void close() { SecurityContextHolder.clearContext(); if (web != null) web.close(); }

    @Test void actualSecurityChainRequiresLinkedCustomerAndRechecksRevocationForBothReads() throws Exception {
        web = new AnnotationConfigWebApplicationContext(); web.setServletContext(new MockServletContext());
        web.register(SupportDeskSecurityFilterTest.SecurityFixture.class, TeamFixture.class); web.refresh();
        var f = web.getBean(SupportDeskFixture.class);
        String ref = "team-" + "a".repeat(64);
        when(web.getBean(JwtDecoder.class).decode("customer-token")).thenReturn(SupportDeskFixture.jwt(
            SupportDeskFixture.ISSUER, "subject-alice", "authenticated", Instant.now().plusSeconds(300)));
        when(f.links.findByIssuerAndSubject(SupportDeskFixture.ISSUER, "subject-alice"))
            .thenReturn(Optional.of(f.identityLinks.get("alice")));
        when(f.desk.get(f.connection, "/api/desk/admin/v1/bindings/support-binding/support-team"))
            .thenReturn(f.json.createArrayNode());
        when(f.desk.getPhoto(f.connection, "/api/desk/admin/v1/bindings/support-binding/support-team/" + ref + "/photo"))
            .thenReturn(new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff});
        SecurityContextHolder.clearContext();
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
        for (String path : new String[] {"/api/support/team", "/api/support/team/" + ref + "/photo"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("X-PLATFORM-API-KEY", "admin-security-key")).andExpect(status().isForbidden());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer actual-agent-token")).andExpect(status().isForbidden());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "private, no-store"));
        }
        verify(web.getBean(CustomerSupabaseUserVerifier.class), times(2)).requireVerifiedUser("customer-token", "subject-alice");
        clearInvocations(f.desk);
        f.identityLinks.get("alice").setRevokedAt(Instant.now());
        for (String path : new String[] {"/api/support/team", "/api/support/team/" + ref + "/photo"}) {
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer customer-token")).andExpect(status().isForbidden());
        }
        verifyNoInteractions(f.desk);
    }

    @Configuration(proxyBeanMethods = false)
    static class TeamFixture {
        @Bean SupportTeamController teamController(SupportDeskFixture f) {
            return new SupportTeamController(new SupportTeamService(f.requesters, f.settings, f.deskBindings, f.targets, f.desk));
        }
    }
}
