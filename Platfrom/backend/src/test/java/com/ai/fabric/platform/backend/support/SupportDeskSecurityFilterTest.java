package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.*;
import com.ai.fabric.platform.backend.deployment.operations.lifecycle.LifecycleGatewayProperties;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.desk.*;
import com.ai.fabric.platform.backend.partner.config.PartnerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.partner.repository.PartnerMemberRepository;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real root security/filter chain and authorization services; only external identity/Desk ports are mocked. */
class SupportDeskSecurityFilterTest {
    private AnnotationConfigWebApplicationContext web;
    private SupportDeskFixture f;
    private MockMvc mvc;
    private SupportDeskContracts.Request lookup;

    @BeforeEach void setup() {
        web = new AnnotationConfigWebApplicationContext(); web.setServletContext(new MockServletContext());
        web.register(SecurityFixture.class); web.refresh();
        f = web.getBean(SupportDeskFixture.class);
        lookup = f.lookup(f.create(false).id());
        SecurityContextHolder.clearContext();
        mvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); if (web != null) web.close(); }

    @Test void actualDeskOnlyIdentityCanLookupAndReadIntakeWithoutConsoleMembership() throws Exception {
        assertThat(f.links.findByIssuerAndSubject(SupportDeskFixture.ISSUER, SupportDeskFixture.SUBJECT)).isEmpty();
        mvc.perform(post("/api/integrations/support/desk/context").headers(f.deskHeaders())
            .contentType(MediaType.APPLICATION_JSON).content(f.json.writeValueAsBytes(lookup)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("AVAILABLE"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        mvc.perform(get("/api/support/staff/intake").header(HttpHeaders.AUTHORIZATION, "Bearer actual-agent-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].reference").isNotEmpty());
        verify(f.desk, times(2)).get(eq(f.connection), contains("permission=SUPPORT"));
        // A path exemption, not a bypass of identity verification: both calls pass the real verifier service.
        verify(web.getBean(CustomerSupabaseUserVerifier.class), times(2)).requireVerifiedUser("actual-agent-token", SupportDeskFixture.SUBJECT);
    }

    @Test void serviceSecretAloneAndAdminCookieOrApiKeyCannotReplaceAgentJwt() throws Exception {
        var serviceOnly = f.deskHeaders(); serviceOnly.remove(HttpHeaders.AUTHORIZATION);
        mvc.perform(post("/api/integrations/support/desk/context").headers(serviceOnly)
            .header("X-PLATFORM-API-KEY", "admin-security-key").cookie(new Cookie("platform_session", "admin-session"))
            .contentType(MediaType.APPLICATION_JSON).content(f.json.writeValueAsBytes(lookup)))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/support/staff/intake").header("X-PLATFORM-API-KEY", "admin-security-key")
            .cookie(new Cookie("platform_session", "admin-session"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/support/staff/intake")).andExpect(status().isForbidden());
        verifyNoInteractions(f.desk);
    }

    @Test void validJwtStillRequiresContextSecretAndCurrentSupportAssignment() throws Exception {
        var jwtOnly = f.deskHeaders(); jwtOnly.remove(SupportDeskAuthorization.SERVICE_HEADER);
        mvc.perform(post("/api/integrations/support/desk/context").headers(jwtOnly)
            .contentType(MediaType.APPLICATION_JSON).content(f.json.writeValueAsBytes(lookup))).andExpect(status().isForbidden());
        when(f.desk.get(eq(f.connection), anyString())).thenReturn(f.json.createObjectNode().put("authorized", false));
        mvc.perform(post("/api/integrations/support/desk/context").headers(f.deskHeaders())
            .contentType(MediaType.APPLICATION_JSON).content(f.json.writeValueAsBytes(lookup))).andExpect(status().isForbidden());
        mvc.perform(get("/api/support/staff/intake").header(HttpHeaders.AUTHORIZATION, "Bearer actual-agent-token"))
            .andExpect(status().isForbidden());
    }

    @Test void invalidOrExpiredJwtCannotFallBackToValidAdminCredentials() throws Exception {
        for (String token : List.of("invalid-token", "expired-token")) {
            var headers = f.deskHeaders(); headers.setBearerAuth(token);
            mvc.perform(post("/api/integrations/support/desk/context").headers(headers)
                .header("X-PLATFORM-API-KEY", "admin-security-key")
                .contentType(MediaType.APPLICATION_JSON).content(f.json.writeValueAsBytes(lookup)))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
            mvc.perform(get("/api/support/staff/intake").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-PLATFORM-API-KEY", "admin-security-key"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
        }
        verifyNoInteractions(f.desk);
    }

    @Test void exactCallbackAndIntakeExceptionsDoNotOpenAdjacentInternalOrCustomerApis() throws Exception {
        for (String path : List.of("/api/internal/support/not-registered", "/api/integrations/support/desk/not-registered",
            "/api/support/staff/intake/not-registered", "/api/support/cases/not-registered")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        // Outside those exact paths, the normal customer bearer filter still refuses an unlinked Desk identity.
        mvc.perform(get("/api/internal/support/not-registered").header(HttpHeaders.AUTHORIZATION, "Bearer actual-agent-token"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("CUSTOMER_IDENTITY_NOT_LINKED"));
        mvc.perform(get("/api/internal/support/not-registered").header("X-PLATFORM-API-KEY", "admin-security-key"))
            .andExpect(status().isOk());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @EnableWebSecurity
    @Import(PlatformSecurityConfiguration.class)
    static class SecurityFixture {
        @Bean SupportDeskFixture fixture() { return new SupportDeskFixture(); }
        @Bean ObjectMapper objectMapper(SupportDeskFixture f) { return f.json; }
        @Bean PlatformAuthProperties platformAuthProperties() {
            return new PlatformAuthProperties(true, "X-PLATFORM-API-KEY", true, true, "platform_session", Duration.ofHours(1),
                false, "Lax", "operator-security-key", "admin-security-key", false, null, null, null);
        }
        @Bean PlatformPublicApiProperties publicApiProperties() { return new PlatformPublicApiProperties(false, "X-CLIENT-ID", "X-CLIENT-KEY", Map.of()); }
        @Bean PlatformConsumerRuntimeAssignmentApiProperties assignmentProperties() { return new PlatformConsumerRuntimeAssignmentApiProperties(false, null, null, null, null); }
        @Bean LifecycleGatewayProperties lifecycle() { return new LifecycleGatewayProperties(); }
        @Bean PartnerSupabaseAuthProperties partnerProperties() { return new PartnerSupabaseAuthProperties(false, null, null, null, null, false, List.of(), null, null); }
        @Bean PlatformSecretService secrets(SupportDeskFixture f) { return f.secrets; }
        @Bean PlatformManagedProductServiceRepository products() { return mock(PlatformManagedProductServiceRepository.class); }
        @Bean PartnerMemberRepository partnerMembers() { return mock(PartnerMemberRepository.class); }
        @Bean DeskActivationService deskActivation() { return mock(DeskActivationService.class); }
        @Bean DeskServiceClient deskClient(SupportDeskFixture f) { return f.desk; }
        @Bean DeploymentTargetProfileRepository targets(SupportDeskFixture f) { return f.targets; }
        @Bean PlatformIdentityService platformIdentityService() {
            var service = mock(PlatformIdentityService.class);
            when(service.authenticateSessionToken("admin-session")).thenReturn(new PlatformPrincipal("admin", PlatformRole.PLATFORM_ADMIN, "Admin", "SESSION", "admin-user"));
            return service;
        }
        @Bean CustomerSupabaseAuthProperties customerProperties() {
            return new CustomerSupabaseAuthProperties(true, "https://identity.example.test", "publishable-test", SupportDeskFixture.ISSUER,
                SupportDeskFixture.ISSUER + "/.well-known/jwks.json", "authenticated", List.of());
        }
        @Bean(name = "customerSupabaseJwtDecoder") JwtDecoder decoder() {
            var decoder = mock(JwtDecoder.class);
            when(decoder.decode(anyString())).thenAnswer(call -> {
                String token = call.getArgument(0);
                if ("invalid-token".equals(token)) throw new JwtException("Rejected test identity");
                return SupportDeskFixture.jwt(SupportDeskFixture.ISSUER, SupportDeskFixture.SUBJECT, "authenticated",
                    Instant.now().plusSeconds("expired-token".equals(token) ? -1 : 300));
            });
            return decoder;
        }
        @Bean CustomerSupabaseUserVerifier userVerifier() { return mock(CustomerSupabaseUserVerifier.class); }
        @Bean CustomerSupabaseIdentityService customerIdentities(CustomerSupabaseAuthProperties properties,
            @Qualifier("customerSupabaseJwtDecoder") ObjectProvider<JwtDecoder> decoder,
            CustomerSupabaseUserVerifier verifier, SupportDeskFixture f) {
            return new CustomerSupabaseIdentityService(properties, decoder, verifier, f.links, f.users,
                new PlatformCustomerAccessService(f.users, f.customers), mock(PlatformAuditService.class));
        }
        @Bean SupportDeskContextController deskContextController(SupportDeskFixture f,
            CustomerSupabaseIdentityService identities, CustomerSupabaseAuthProperties policy) {
            var auth = new SupportDeskAuthorization(f.settings, f.secrets, identities, policy, f.deskBindings, f.targets, f.desk);
            var service = f.transactional(new SupportDeskContextService(auth, f.settings, f.store, f.deskStore, f.requesters, f.resources, f.customers));
            return new SupportDeskContextController(service);
        }
        @Bean ProtectedProbes probes() { return new ProtectedProbes(); }
    }

    @RestController
    static class ProtectedProbes {
        @GetMapping({"/api/internal/support/not-registered", "/api/integrations/support/desk/not-registered",
            "/api/support/staff/intake/not-registered", "/api/support/cases/not-registered"})
        Map<String, Boolean> protectedApi() { return Map.of("reached", true); }
    }
}
