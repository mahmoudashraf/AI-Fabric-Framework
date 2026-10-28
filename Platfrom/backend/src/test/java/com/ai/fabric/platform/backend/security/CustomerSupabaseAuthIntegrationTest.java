package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.security.repository.PlatformCustomerIdentityLinkRepository;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseUserVerifier;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "platform.auth.enabled=true", "platform.auth.admin-api-key=staff-key", "platform.auth.session-enabled=true",
    "platform.auth.customer-supabase.enabled=true", "platform.auth.customer-supabase.project-url=https://auth.example.test",
    "platform.auth.customer-supabase.issuer=https://auth.example.test/auth/v1",
    "platform.auth.customer-supabase.jwks-uri=https://auth.example.test/auth/v1/.well-known/jwks.json",
    "platform.auth.customer-supabase.public-key=sb_publishable_test_only_public_key",
    "platform.auth.customer-supabase.link-origins[0]=https://console.example.test",
    "platform.auth.customer-supabase.desk-clients[0].client-id=desk-test",
    "platform.auth.customer-supabase.desk-clients[0].redirect-uris[0]=https://desk.example.test/auth/callback",
    "platform.cors.allowed-origins[0]=https://console.example.test",
    "platform.cors.allowed-origins[1]=https://loomai.example.test",
    "platform.cors.allowed-origins[2]=https://shop.example.test",
    "platform.cors.allowed-origins[3]=https://partner.example.test",
    "platform.cors.allowed-origin-patterns[0]=https://*.apps.example.test",
    "platform.cors.allow-credentials=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomerSupabaseAuthIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired PlatformCustomerIdentityLinkRepository links;
    @Autowired com.ai.fabric.platform.backend.security.repository.PlatformUserRepository users;
    @Autowired com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository customers;
    @MockBean(name = "customerSupabaseJwtDecoder") JwtDecoder decoder;
    @MockBean CustomerSupabaseUserVerifier verifier;

    @BeforeEach void tokens() {
        when(decoder.decode(anyString())).thenAnswer(call -> {
            String token = call.getArgument(0);
            if (token.equals("invalid") || token.equals("expired")) throw new JwtException("Invalid token");
            var jwt = Jwt.withTokenValue(token).header("alg", "ES256").issuer("https://auth.example.test/auth/v1")
                .subject(token.equals("delegated-owner") ? "subject-owner" : token).audience(java.util.List.of("authenticated")).issuedAt(Instant.now().minusSeconds(10))
                .expiresAt(Instant.now().plusSeconds(300)).claim("email", token.startsWith("signup-")
                    ? token + "@onboarding.example.test" : "same-email@not-an-identity.test");
            if (token.equals("delegated-owner")) jwt.claim("client_id", "desk-test");
            return jwt.build();
        });
    }

    @Test void safePublicConfigurationDoesNotRequireConsoleMembership() throws Exception {
        mvc.perform(get("/api/platform/auth/customer-config")).andExpect(status().isOk())
            .andExpect(jsonPath("$.publicKey", is("sb_publishable_test_only_public_key")))
            .andExpect(jsonPath("$.deskClients[0].clientId", is("desk-test")))
            .andExpect(jsonPath("$.clientSecret").doesNotExist());
    }

    @Test void dualProofLinkPreservesOwnershipAndDisablesLocalCustomerSessions() throws Exception {
        String customer = customer("identity-owner"), other = customer("identity-other");
        String user = user("linked-owner@example.test", customer);
        Cookie cookie = login("linked-owner@example.test");
        mvc.perform(linkRequest().cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content("{\"accessToken\":\"subject-owner\"}")).andExpect(status().isOk());
        assertThat(links.findByPlatformUserId(user).orElseThrow().getSubject()).isEqualTo("subject-owner");
        mvc.perform(get("/api/platform/auth/session").header("Authorization", "Bearer subject-owner"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.authenticationMode", is("SUPABASE")))
            .andExpect(jsonPath("$.customerId", is(customer))).andExpect(jsonPath("$.actorId", is("linked-owner@example.test")))
            .andExpect(jsonPath("$.canManageSecrets", is(false)));
        mvc.perform(get("/api/platform/auth/session").header("Authorization", "Bearer delegated-owner"))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/platform/customers").header("Authorization", "Bearer subject-owner"))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id", is(customer)));
        mvc.perform(put("/api/platform/customers/" + other).header("Authorization", "Bearer subject-owner")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Cross customer\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/auth/session").cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated", is(false)));
        mvc.perform(post("/api/platform/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"linked-owner@example.test\",\"password\":\"CustomerPass123!\"}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode", is("CUSTOMER_USE_SUPABASE")))
            .andExpect(jsonPath("$.useSupabase", is(true)));
    }

    @Test void unlinkedSameEmailIdentityIsNotAnOwnerAndCannotFallBackToStaffCredentials() throws Exception {
        user("same-email@not-an-identity.test", customer("email-collision"));
        mvc.perform(get("/api/platform/auth/session").header("Authorization", "Bearer unlinked-same-email")
            .header("X-PLATFORM-API-KEY", "staff-key")).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode", is("CUSTOMER_IDENTITY_NOT_LINKED")));
        mvc.perform(get("/api/platform/customers").header("Authorization", "Bearer invalid")
            .header("X-PLATFORM-API-KEY", "staff-key")).andExpect(status().isUnauthorized());
    }

    @Test void linkingRequiresBothProofsAndSubjectCannotBeAssignedTwice() throws Exception {
        String a = customer("link-a"), b = customer("link-b");
        user("link-a@example.test", a); user("link-b@example.test", b);
        Cookie ca = login("link-a@example.test"), cb = login("link-b@example.test");
        mvc.perform(linkRequest().header("X-PLATFORM-API-KEY", "staff-key")
            .contentType(MediaType.APPLICATION_JSON).content("{\"accessToken\":\"unique-sub\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(linkRequest().cookie(ca).contentType(MediaType.APPLICATION_JSON)
            .content("{\"accessToken\":\"expired\"}")).andExpect(status().isUnauthorized());
        mvc.perform(linkRequest().cookie(ca).contentType(MediaType.APPLICATION_JSON)
            .content("{\"accessToken\":\"unique-sub\"}")).andExpect(status().isOk());
        mvc.perform(linkRequest().cookie(cb).contentType(MediaType.APPLICATION_JSON)
            .content("{\"accessToken\":\"unique-sub\"}")).andExpect(status().isConflict());
        mvc.perform(linkRequest().header("Authorization", "Bearer unique-sub")
            .contentType(MediaType.APPLICATION_JSON).content("{\"accessToken\":\"another-sub\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void revokedMappingCannotUseJwtOrReenableLocalPassword() throws Exception {
        String c = customer("revoke"), u = user("revoke@example.test", c);
        Cookie cookie = login("revoke@example.test");
        mvc.perform(linkRequest().cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content("{\"accessToken\":\"revocable-sub\"}")).andExpect(status().isOk());
        mvc.perform(delete("/api/platform/auth/customer-links/" + u).header("X-PLATFORM-API-KEY", "staff-key"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/platform/auth/session").header("Authorization", "Bearer revocable-sub"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"revoke@example.test\",\"password\":\"CustomerPass123!\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test void unverifiedProviderIdentityDoesNotLink() throws Exception {
        String c = customer("unverified"); user("unverified@example.test", c);
        Cookie cookie = login("unverified@example.test");
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED))
            .when(verifier).requireVerifiedUser("unverified-sub", "unverified-sub");
        mvc.perform(linkRequest().cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content("{\"accessToken\":\"unverified-sub\"}")).andExpect(status().isUnauthorized());
    }

    @Test void disabledCustomerMembershipIsCheckedOnEveryRequest() throws Exception {
        String id = user("inactive-owner@example.test", customer("inactive-owner"));
        Cookie cookie = login("inactive-owner@example.test");
        mvc.perform(linkRequest().cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content("{\"accessToken\":\"inactive-owner-sub\"}")).andExpect(status().isOk());
        var user = users.findById(id).orElseThrow(); user.setStatus("DISABLED"); users.saveAndFlush(user);
        mvc.perform(get("/api/platform/auth/session").header("Authorization", "Bearer inactive-owner-sub"))
            .andExpect(status().isUnauthorized());
    }

    @Test void staffPasswordSessionsStillWorkWithCustomerSupabaseEnabled() throws Exception {
        mvc.perform(post("/api/platform/users").header("X-PLATFORM-API-KEY", "staff-key")
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"local-staff@example.test\",\"displayName\":\"Staff\",\"password\":\"CustomerPass123!\",\"role\":\"PLATFORM_OPERATOR\"}"))
            .andExpect(status().isCreated());
        Cookie cookie = login("local-staff@example.test");
        mvc.perform(get("/api/platform/auth/session").cookie(cookie)).andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticationMode", is("SESSION"))).andExpect(jsonPath("$.role", is("PLATFORM_OPERATOR")));
        mvc.perform(get("/api/platform/overview").cookie(cookie)).andExpect(status().isOk());
    }

    @Test void verifiedSignupCreatesOnlyItsOwnWorkspaceAndRetriesDoNotCreateAnother() throws Exception {
        String token = "signup-fresh";
        mvc.perform(get("/api/platform/auth/customer-onboarding").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("CREATE_WORKSPACE")));
        var created = mvc.perform(onboard(token).content("{\"workspaceName\":\"New company\",\"displayName\":\"New owner\",\"role\":\"PLATFORM_ADMIN\",\"customerId\":\"cust-internal\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("READY"))).andReturn();
        String customerId = JsonPath.read(created.getResponse().getContentAsString(), "$.customerId");
        var owner = users.findByEmailIgnoreCase(token + "@onboarding.example.test").orElseThrow();
        assertThat(owner.getRole()).isEqualTo("CUSTOMER_ADMIN");
        assertThat(owner.getCustomerId()).isEqualTo(customerId).isNotEqualTo("cust-internal");
        assertThat(customers.findById(customerId).orElseThrow().isPlatformManaged()).isFalse();
        mvc.perform(onboard(token).content("{\"workspaceName\":\"Ignored rename\",\"displayName\":\"Ignored owner\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.customerId", is(customerId)))
            .andExpect(jsonPath("$.workspaceName", is("New company")));
        mvc.perform(get("/api/platform/auth/session").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.customerId", is(customerId)))
            .andExpect(jsonPath("$.role", is("CUSTOMER_ADMIN"))).andExpect(jsonPath("$.canManageSecrets", is(false)));
        mvc.perform(get("/api/platform/customers").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id", is(customerId)));
        mvc.perform(post("/api/customer-self-service/enrollment").header("Authorization", "Bearer " + token)
            .header(HttpHeaders.ORIGIN, "https://console.example.test"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/customer-self-service/catalogue").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test void templateEnrollmentRejectsNonConsoleOrigins() throws Exception {
        mvc.perform(onboard("signup-enrollment-origin").content("{\"workspaceName\":\"Enrollment origin\",\"displayName\":\"Owner\"}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/customer-self-service/enrollment").header("Authorization", "Bearer signup-enrollment-origin")
            .header(HttpHeaders.ORIGIN, "https://dealer.apps.example.test")).andExpect(status().isForbidden());
        mvc.perform(post("/api/customer-self-service/enrollment").header("Authorization", "Bearer signup-enrollment-origin"))
            .andExpect(status().isForbidden());
    }

    @Test void workspaceRegistrationNeverClaimsAnExistingEmailOrRevivesRevokedMembership() throws Exception {
        user("signup-collision@onboarding.example.test", customer("signup-existing"));
        mvc.perform(get("/api/platform/auth/customer-onboarding").header("Authorization", "Bearer signup-collision"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status", is("LINK_REQUIRED")));
        mvc.perform(onboard("signup-collision").content("{\"workspaceName\":\"Claim existing\",\"displayName\":\"Owner\"}"))
            .andExpect(status().isConflict());
        assertThat(links.findByIssuerAndSubject("https://auth.example.test/auth/v1", "signup-collision")).isEmpty();
        mvc.perform(onboard("signup-revoked").content("{\"workspaceName\":\"Revoked workspace\",\"displayName\":\"Owner\"}"))
            .andExpect(status().isOk());
        var owner = users.findByEmailIgnoreCase("signup-revoked@onboarding.example.test").orElseThrow();
        mvc.perform(delete("/api/platform/auth/customer-links/" + owner.getId()).header("X-PLATFORM-API-KEY", "staff-key"))
            .andExpect(status().isOk());
        mvc.perform(onboard("signup-revoked").content("{\"workspaceName\":\"Replacement\",\"displayName\":\"Owner\"}"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/auth/customer-onboarding").header("Authorization", "Bearer signup-revoked"))
            .andExpect(status().isForbidden());
    }

    @Test void onboardingRequiresVerifiedDirectIdentityAndTrustedOrigin() throws Exception {
        String body = "{\"workspaceName\":\"Blocked\",\"displayName\":\"Owner\"}";
        long initial = customers.count();
        mvc.perform(post("/api/platform/auth/customer-onboarding").header(HttpHeaders.ORIGIN, "https://console.example.test")
            .header("X-PLATFORM-API-KEY", "staff-key").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnauthorized());
        mvc.perform(onboard("invalid").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(onboard("delegated-owner").content(body)).andExpect(status().isForbidden());
        doThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED))
            .when(verifier).requireVerifiedUser("signup-unverified", "signup-unverified");
        mvc.perform(onboard("signup-unverified").content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/platform/auth/customer-onboarding").header("Authorization", "Bearer signup-foreign-origin")
            .header(HttpHeaders.ORIGIN, "https://dealer.apps.example.test").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isForbidden());
        mvc.perform(onboard("signup-invalid-name").content("{\"workspaceName\":\" \",\"displayName\":\"Owner\"}"))
            .andExpect(status().isBadRequest());
        assertThat(customers.count()).isEqualTo(initial);
    }

    private MockHttpServletRequestBuilder onboard(String token) {
        return post("/api/platform/auth/customer-onboarding").header("Authorization", "Bearer " + token)
            .header(HttpHeaders.ORIGIN, "https://console.example.test").contentType(MediaType.APPLICATION_JSON);
    }

    @Test void linkingRejectsMissingOriginBeforeIdentityVerificationOrMutation() throws Exception {
        String user = user("missing-origin@example.test", customer("missing-origin"));
        Cookie cookie = login("missing-origin@example.test");
        mvc.perform(post("/api/platform/auth/customer-link").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
            .content("{\"accessToken\":\"missing-origin-sub\"}"))
            .andExpect(status().isForbidden())
            .andExpect(result -> assertThat(result.getResolvedException())
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class));
        assertThat(links.findByPlatformUserId(user)).isEmpty();
        verifyNoInteractions(decoder, verifier);
    }

    @Test void linkingRejectsPublicPartnerAndCustomerAppOriginsEvenWhenCorsAllowsThem() throws Exception {
        String user = user("untrusted-origin@example.test", customer("untrusted-origin"));
        Cookie cookie = login("untrusted-origin@example.test");
        for (String origin : java.util.List.of("https://loomai.example.test", "https://shop.example.test",
            "https://partner.example.test", "https://dealer.apps.example.test")) {
            mvc.perform(post("/api/platform/auth/customer-link").header(HttpHeaders.ORIGIN, origin)
                .cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"accessToken\":\"untrusted-origin-sub\"}"))
                .andExpect(status().isForbidden())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(result -> assertThat(result.getResolvedException())
                    .isInstanceOf(org.springframework.web.server.ResponseStatusException.class));
            assertThat(links.findByPlatformUserId(user)).isEmpty();
        }
        verifyNoInteractions(decoder, verifier);
    }

    @Test void linkingRejectsUnknownAndOpaqueOriginsWithoutCreatingALink() throws Exception {
        String user = user("unknown-origin@example.test", customer("unknown-origin"));
        Cookie cookie = login("unknown-origin@example.test");
        for (String origin : java.util.List.of("https://unknown.example.test", "https://console.example.test.attacker.test", "null")) {
            mvc.perform(post("/api/platform/auth/customer-link").header(HttpHeaders.ORIGIN, origin)
                .cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{\"accessToken\":\"unknown-origin-sub\"}"))
                .andExpect(status().isForbidden());
            assertThat(links.findByPlatformUserId(user)).isEmpty();
        }
        verifyNoInteractions(decoder, verifier);
    }

    private MockHttpServletRequestBuilder linkRequest() {
        return post("/api/platform/auth/customer-link").header(HttpHeaders.ORIGIN, "https://console.example.test");
    }

    private String customer(String name) throws Exception {
        var result = mvc.perform(post("/api/platform/customers").header("X-PLATFORM-API-KEY", "staff-key")
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
            .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
    private String user(String email, String customer) throws Exception {
        var result = mvc.perform(post("/api/platform/users").header("X-PLATFORM-API-KEY", "staff-key")
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"" + email + "\",\"displayName\":\"Owner\",\"password\":\"CustomerPass123!\",\"role\":\"CUSTOMER_ADMIN\",\"customerId\":\"" + customer + "\"}"))
            .andExpect(status().isCreated()).andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }
    private Cookie login(String email) throws Exception {
        var result = mvc.perform(post("/api/platform/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"CustomerPass123!\"}"))
            .andExpect(status().isOk()).andReturn();
        return new Cookie("platform_session", result.getResponse().getCookie("platform_session").getValue());
    }
}
