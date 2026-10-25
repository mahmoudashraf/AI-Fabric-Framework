package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.*;

class LifecycleMachineFilterTest {
    private final LifecycleGatewayProperties properties = new LifecycleGatewayProperties();
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void dedicatedKeyOnlyEntersItsOwnRouteAndDoesNotImpersonateAnOperator() throws Exception {
        properties.setEnabled(true); properties.setApiKey("secret-" + "x".repeat(40));
        var request = request(); request.addHeader("Authorization", "Bearer " + properties.getApiKey());
        var response = new MockHttpServletResponse();
        new LifecycleMachineFilter(properties).doFilter(request, response, (incoming, outgoing) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertThat(auth.getAuthorities()).extracting("authority").containsExactly(LifecycleMachineFilter.AUTHORITY);
            assertThat(auth.getPrincipal()).isEqualTo("deployment-operations-service");
            assertThat(com.ai.fabric.platform.backend.security.PlatformSecurityContext.currentPrincipal()).isNull();
        });
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        var outside = new MockHttpServletRequest("POST", "/api/deployments");
        outside.addHeader("Authorization", "Bearer " + properties.getApiKey());
        new LifecycleMachineFilter(properties).doFilter(outside, new MockHttpServletResponse(), (incoming, outgoing) ->
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull());
    }

    @Test void disabledMissingWrongAndDuplicateCredentialsAreRejected() throws Exception {
        for (int variant = 0; variant < 4; variant++) {
            properties.setEnabled(variant != 0); properties.setApiKey("x".repeat(40));
            var request = request();
            if (variant != 1) request.addHeader("Authorization", "Bearer " + (variant == 2 ? "wrong" : properties.getApiKey()));
            if (variant == 3) request.addHeader("Authorization", "Bearer " + properties.getApiKey());
            var response = new MockHttpServletResponse(); var invoked = new AtomicBoolean();
            new LifecycleMachineFilter(properties).doFilter(request, response, (incoming, outgoing) -> invoked.set(true));
            assertThat(response.getStatus()).isEqualTo(401); assertThat(invoked).isFalse();
        }
    }
    @Test void authenticatedBodyIsBoundedAndEncodingCannotBypassTheLimit() throws Exception {
        properties.setEnabled(true); properties.setApiKey("x".repeat(40));
        var tooLarge = request(); tooLarge.addHeader("Authorization", "Bearer " + properties.getApiKey());
        tooLarge.setContent(new byte[8193]); var response = new MockHttpServletResponse();
        new LifecycleMachineFilter(properties).doFilter(tooLarge, response, (incoming, outgoing) -> { throw new AssertionError(); });
        assertThat(response.getStatus()).isEqualTo(413);
        var encoded = request(); encoded.addHeader("Authorization", "Bearer " + properties.getApiKey());
        encoded.addHeader("Content-Encoding", "gzip"); response = new MockHttpServletResponse();
        new LifecycleMachineFilter(properties).doFilter(encoded, response, (incoming, outgoing) -> { throw new AssertionError(); });
        assertThat(response.getStatus()).isEqualTo(415);
    }
    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest("POST", LifecycleMachineFilter.PREFIX + "commands");
        request.setContentType("application/json"); return request;
    }
}
