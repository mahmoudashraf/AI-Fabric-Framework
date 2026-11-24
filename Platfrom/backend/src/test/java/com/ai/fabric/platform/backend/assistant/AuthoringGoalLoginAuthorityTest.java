package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.security.*;
import com.ai.fabric.platform.backend.security.entity.*;
import com.ai.fabric.platform.backend.security.repository.*;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseIdentityService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthoringGoalLoginAuthorityTest {
    final PlatformUserSessionRepository sessions = mock(PlatformUserSessionRepository.class);
    final PlatformCustomerIdentityLinkRepository links = mock(PlatformCustomerIdentityLinkRepository.class);
    final CustomerSupabaseIdentityService identities = mock(CustomerSupabaseIdentityService.class);
    final AuthoringGoalLoginAuthority authority = new AuthoringGoalLoginAuthority(
        new PlatformAuthProperties(true, "header", false, true, "login-cookie", Duration.ofHours(1), false, "Lax", "", "", false, "", "", ""),
        sessions, links, identities, new ObjectMapper());
    final PlatformPrincipal principal = new PlatformPrincipal("author@example.test", PlatformRole.PLATFORM_ADMIN, "Author", "SESSION", "user-id");
    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }
    @Test void nativeGrantStoresOnlyVerifiedLoginReferenceAndChecksRevocation() {
        var request = new MockHttpServletRequest(); request.setCookies(new Cookie("login-cookie", "private-browser-cookie"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var login = new PlatformUserSessionEntity(); login.setId("login-reference"); login.setUserId("user-id"); login.setExpiresAt(Instant.now().plusSeconds(3600));
        when(sessions.findByTokenHash(anyString())).thenReturn(Optional.of(login)); when(sessions.findById("login-reference")).thenReturn(Optional.of(login));
        var grant = authority.capture(principal); assertThat(grant.path("loginSessionId").asText()).isEqualTo("login-reference");
        assertThat(grant.toString()).doesNotContain("private-browser-cookie"); authority.requireCurrent(grant, principal);
        login.setRevokedAt(Instant.now());
        assertThatThrownBy(() -> authority.requireCurrent(grant, principal)).hasMessageContaining("REAUTHENTICATION_REQUIRED");
    }
    @Test void unverifiedProcessContextCannotMintPersistentLoginAuthority() {
        assertThatThrownBy(() -> authority.capture(principal)).hasMessageContaining("REAUTHENTICATION_REQUIRED");
    }
    @Test void customerGrantPinsVerifiedIdentityLinkAndTokenExpiryWithoutRetainingToken() {
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer private-customer-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var customer = new PlatformPrincipal("customer@example.test", PlatformRole.CUSTOMER_ADMIN, "Customer", "SUPABASE", "customer-user");
        var jwt = Jwt.withTokenValue("private-customer-token").header("alg", "RS256").subject("subject")
            .issuer("https://identity.example.test").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        when(identities.authenticate("private-customer-token")).thenReturn(customer);
        when(identities.verifyIdentity("private-customer-token")).thenReturn(jwt);
        var link = new PlatformCustomerIdentityLinkEntity(); link.setId("link-ref"); link.setPlatformUserId("customer-user");
        when(links.findByPlatformUserId("customer-user")).thenReturn(Optional.of(link)); when(links.findById("link-ref")).thenReturn(Optional.of(link));
        var grant = authority.capture(customer); assertThat(grant.path("identityLinkId").asText()).isEqualTo("link-ref");
        assertThat(grant.toString()).doesNotContain("private-customer-token"); authority.requireCurrent(grant, customer);
        link.setRevokedAt(Instant.now()); assertThatThrownBy(() -> authority.requireCurrent(grant, customer)).hasMessageContaining("REAUTHENTICATION_REQUIRED");
    }
}
