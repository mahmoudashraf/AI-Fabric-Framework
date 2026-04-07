package com.ai.fabric.runtime.auth;

import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeRequestAuthResolverTest {

    @Test
    void verifiedHeaderAuthPassesWhenIssuerAndAudienceMatchConfiguredPolicy() {
        RuntimeRequestAuthResolver resolver = new RuntimeRequestAuthResolver(strictProperties());
        MockHttpServletRequest request = verifiedRequest("trusted-backend-app", "dep-123,chat-surface");

        RuntimeResolvedIdentity identity = resolver.resolveVerifiedForChat(request, null, null);

        assertThat(identity.getAuthContext().getSubjectId()).isEqualTo("customer-123");
        assertThat(identity.getAuthContext().getIssuer()).isEqualTo("trusted-backend-app");
        assertThat(identity.getAuthContext().getAudiences()).containsExactly("dep-123", "chat-surface");
    }

    @Test
    void verifiedHeaderAuthRejectsUntrustedIssuer() {
        RuntimeRequestAuthResolver resolver = new RuntimeRequestAuthResolver(strictProperties());
        MockHttpServletRequest request = verifiedRequest("rogue-backend", "dep-123");

        assertThatThrownBy(() -> resolver.resolveVerifiedForChat(request, null, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401 UNAUTHORIZED")
            .hasMessageContaining("Runtime verified auth issuer is not allowed");
    }

    @Test
    void verifiedHeaderAuthRejectsMissingAudienceWhenPolicyConfigured() {
        RuntimeRequestAuthResolver resolver = new RuntimeRequestAuthResolver(strictProperties());
        MockHttpServletRequest request = verifiedRequest("trusted-backend-app", null);

        assertThatThrownBy(() -> resolver.resolveVerifiedForChat(request, null, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401 UNAUTHORIZED")
            .hasMessageContaining("Runtime verified auth audience is not allowed");
    }

    @Test
    void verifiedHeaderAuthRejectsNonMatchingAudience() {
        RuntimeRequestAuthResolver resolver = new RuntimeRequestAuthResolver(strictProperties());
        MockHttpServletRequest request = verifiedRequest("trusted-backend-app", "other-deployment");

        assertThatThrownBy(() -> resolver.resolveVerifiedForChat(request, null, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401 UNAUTHORIZED")
            .hasMessageContaining("Runtime verified auth audience is not allowed");
    }

    private RuntimeAuthProperties strictProperties() {
        RuntimeAuthProperties properties = new RuntimeAuthProperties();
        properties.getIngress().setMode(RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED);
        properties.getIngress().getTrustedBackend().setApiKeyValue("runtime-secret");
        properties.getIngress().setAcceptedIssuers(List.of("trusted-backend-app"));
        properties.getIngress().setAcceptedAudiences(List.of("dep-123"));
        return properties;
    }

    private MockHttpServletRequest verifiedRequest(String issuer, String audiences) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-AIFABRIC-RUNTIME-API-KEY", "runtime-secret");
        request.addHeader("X-AIFABRIC-AUTH-SUBJECT-ID", "customer-123");
        request.addHeader("X-AIFABRIC-AUTH-SUBJECT-TYPE", RuntimeAuthSubjectType.END_USER.name());
        request.addHeader("X-AIFABRIC-AUTH-MODE", RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED.name());
        request.addHeader("X-AIFABRIC-AUTH-CALLER-TYPE", RuntimeAuthCallerType.TRUSTED_BACKEND.name());
        request.addHeader("X-AIFABRIC-AUTH-SESSION-ID", "session-123");
        request.addHeader("X-AIFABRIC-AUTH-DEPLOYMENT-ID", "dep-123");
        request.addHeader("X-AIFABRIC-AUTH-ISSUER", issuer);
        if (audiences != null) {
            request.addHeader("X-AIFABRIC-AUTH-AUDIENCES", audiences);
        }
        return request;
    }
}
