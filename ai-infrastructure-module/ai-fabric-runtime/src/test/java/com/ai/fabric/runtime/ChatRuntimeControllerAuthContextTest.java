package com.ai.fabric.runtime;

import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.chat.RuntimeConversationGateway;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.ai.fabric.runtime.web.ChatRuntimeController;
import com.ai.fabric.runtime.web.dto.RuntimeAuthContextResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.EXPIRES;
import static org.springframework.http.HttpHeaders.PRAGMA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatRuntimeControllerAuthContextTest {

    @Test
    void myAuthContextUsesVerifiedRuntimeIdentity() {
        ChatRuntimeController controller = instantiateController(strictAuthResolver());

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        ResponseEntity<RuntimeAuthContextResponse> responseEntity = controller.authContext(servletRequest);
        RuntimeAuthContextResponse response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getSubjectId()).isEqualTo("verified-user");
        assertThat(response.getSubjectType()).isEqualTo(RuntimeAuthSubjectType.END_USER.name());
        assertThat(response.getAuthMode()).isEqualTo(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED.name());
        assertThat(response.getCallerType()).isEqualTo(RuntimeAuthCallerType.TRUSTED_BACKEND.name());
        assertThat(response.getSessionId()).isEqualTo("verified-session");
        assertThat(response.getDeploymentId()).isEqualTo("dep-123");
        assertThat(response.getIssuer()).isEqualTo("backend-test");
        assertThat(response.getAudiences()).containsExactly("dep-123");
        assertThat(responseEntity.getHeaders().getFirst(CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(responseEntity.getHeaders().getFirst(PRAGMA)).isEqualTo("no-cache");
        assertThat(responseEntity.getHeaders().getFirst(EXPIRES)).isEqualTo("0");
    }

    @Test
    void myAuthContextRequiresVerifiedAuthContext() {
        ChatRuntimeController controller = instantiateController(strictAuthResolver());

        assertThatThrownBy(() -> controller.authContext(new MockHttpServletRequest()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401 UNAUTHORIZED")
            .hasMessageContaining("Verified runtime auth context is required");
    }

    @Test
    void authContextAcceptsOnlyAuthContextScopeForStablePlatformProxyIdentity() {
        ChatRuntimeController controller = instantiateController(strictAuthResolver());
        MockHttpServletRequest request = new MockHttpServletRequest();
        RuntimePrivateAssertionTestSupport.addPrivateRuntimeHeaders(request, authProperties(), RuntimeAuthContext.builder()
            .subjectId("actual-customer-principal")
            .subjectType(RuntimeAuthSubjectType.INTERNAL_PLATFORM_USER)
            .authMode(RuntimeAuthMode.PLATFORM_PROXY_SESSION)
            .callerType(RuntimeAuthCallerType.PLATFORM_PROXY)
            .sessionId("platform-poc-dep-123-stable-session")
            .deploymentId("dep-123")
            .issuer("backend-test")
            .audiences(List.of("dep-123"))
            .grantedScopes(List.of("chat:auth-context"))
            .expiresAt(Instant.now().plusSeconds(300))
            .build());

        RuntimeAuthContextResponse response = controller.authContext(request).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getSubjectId()).isEqualTo("actual-customer-principal");
        assertThat(response.getSessionId()).isEqualTo("platform-poc-dep-123-stable-session");
        assertThat(response.getGrantedScopes()).containsExactly("chat:auth-context");
        assertThat(response.getAuthMode()).isEqualTo("PLATFORM_PROXY_SESSION");
    }

    @Test
    void myAuthContextRejectsLegacyQueryParamsOnAuthAwareRoute() {
        ChatRuntimeController controller = instantiateController(strictAuthResolver());

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");
        servletRequest.setParameter("ownerId", "legacy-owner");
        servletRequest.setParameter("sessionId", "legacy-session");

        assertThatThrownBy(() -> controller.authContext(servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("Legacy request identity fields are not allowed on verified runtime endpoint /api/chat/me/auth-context")
            .hasMessageContaining("ownerId, sessionId");
    }

    @Test
    void authContextRejectsLegacyQueryParamsEvenWhenVerifiedIdentityMatches() {
        ChatRuntimeController controller = instantiateController(strictAuthResolver());

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");
        servletRequest.setParameter("userId", "verified-user");

        assertThatThrownBy(() -> controller.authContext(servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("Legacy request identity fields are not allowed on verified runtime endpoint /api/chat/me/auth-context")
            .hasMessageContaining("userId");
    }

    private RuntimeRequestAuthResolver strictAuthResolver() {
        RuntimeAuthProperties properties = authProperties();
        properties.getIngress().setAcceptedIssuers(List.of("backend-test"));
        properties.getIngress().setAcceptedAudiences(List.of("dep-123"));
        return new RuntimeRequestAuthResolver(properties);
    }

    private void addVerifiedAuthHeaders(MockHttpServletRequest request, String subjectId, String sessionId) {
        RuntimePrivateAssertionTestSupport.addPrivateRuntimeHeaders(
            request,
            authProperties(),
            RuntimeAuthContext.builder()
                .subjectId(subjectId)
                .subjectType(RuntimeAuthSubjectType.END_USER)
                .authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
                .callerType(RuntimeAuthCallerType.TRUSTED_BACKEND)
                .sessionId(sessionId)
                .deploymentId("dep-123")
                .issuer("backend-test")
                .audiences(List.of("dep-123"))
                .expiresAt(Instant.now().plusSeconds(300))
                .build()
        );
    }

    private RuntimeAuthProperties authProperties() {
        RuntimeAuthProperties properties = RuntimePrivateAssertionTestSupport.strictPrivateRuntimeProperties();
        properties.getIngress().setAcceptedIssuers(List.of("backend-test"));
        properties.getIngress().setAcceptedAudiences(List.of("dep-123"));
        return properties;
    }

    private ChatRuntimeController instantiateController(RuntimeRequestAuthResolver authResolver) {
        try {
            Constructor<?> constructor = ChatRuntimeController.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return (ChatRuntimeController) constructor.newInstance(
                provider(null),
                mock(RuntimeConversationGateway.class),
                provider(null),
                provider(null),
                provider(null),
                provider(null),
                provider(null),
                authResolver
            );
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ObjectProvider provider(Object value) {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
