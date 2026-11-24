package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseIdentityService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

class AuthoringGoalLogoutConfigurationTest {
    final AuthoringGoalService goals = mock(AuthoringGoalService.class);
    final CustomerSupabaseIdentityService identities = mock(CustomerSupabaseIdentityService.class);

    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    HandlerInterceptor interceptor() {
        var registry = new CapturingRegistry();
        new AuthoringGoalLogoutConfiguration(goals, identities).addInterceptors(registry);
        return registry.interceptor;
    }
    void signInNative() {
        var principal = new PlatformPrincipal("native@example.test", PlatformRole.PLATFORM_ADMIN, "Native", "SESSION", "native-user");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
    @Test void verifiedBearerLogoutRevokesThatActorEvenWhenAnOldNativeCookiePrincipalExists() throws Exception {
        signInNative();
        var bearer = new PlatformPrincipal("customer@example.test", PlatformRole.CUSTOMER_ADMIN, "Customer", "SUPABASE", "customer-user");
        when(identities.authenticate("verified-ephemeral-token")).thenReturn(bearer);
        var request = new MockHttpServletRequest("POST", "/api/platform/auth/logout");
        request.addHeader("Authorization", "Bearer verified-ephemeral-token");
        assertThat(interceptor().preHandle(request, new MockHttpServletResponse(), new Object())).isTrue();
        verify(goals).revokeActor("customer@example.test");
        verify(goals, never()).revokeActor("native@example.test");
    }
    @Test void nativeLogoutRevokesTheCurrentActorButReadingTheRouteDoesNot() throws Exception {
        signInNative(); var interceptor = interceptor();
        interceptor.preHandle(new MockHttpServletRequest("GET", "/api/platform/auth/logout"), new MockHttpServletResponse(), new Object());
        verifyNoInteractions(goals, identities);
        interceptor.preHandle(new MockHttpServletRequest("POST", "/api/platform/auth/logout"), new MockHttpServletResponse(), new Object());
        verify(goals).revokeActor("native@example.test");
    }
    @Test void invalidBearerCannotRevokeAnUnverifiedActorAndStillAllowsNativeCookieLogout() throws Exception {
        when(identities.authenticate("invalid-token")).thenThrow(new ResponseStatusException(UNAUTHORIZED));
        var request = new MockHttpServletRequest("POST", "/api/platform/auth/logout");
        request.addHeader("Authorization", "Bearer invalid-token");
        var interceptor = interceptor();
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        verifyNoInteractions(goals);
        signInNative();
        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());
        verify(goals).revokeActor("native@example.test");
    }
    static class CapturingRegistry extends InterceptorRegistry {
        HandlerInterceptor interceptor;
        @Override public InterceptorRegistration addInterceptor(HandlerInterceptor value) {
            interceptor = value;
            return super.addInterceptor(value);
        }
    }
}
