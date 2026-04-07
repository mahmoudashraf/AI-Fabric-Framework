package com.ai.fabric.runtime;

import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthIngressMode;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatRuntimeControllerAuthContextTest {

    @Test
    void myAuthContextUsesVerifiedRuntimeIdentity() {
        ChatRuntimeController controller = instantiateController(strictAuthResolver());

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        ResponseEntity<RuntimeAuthContextResponse> responseEntity = controller.myAuthContext(servletRequest);
        RuntimeAuthContextResponse response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getSubjectId()).isEqualTo("verified-user");
        assertThat(response.getSubjectType()).isEqualTo(RuntimeAuthSubjectType.END_USER.name());
        assertThat(response.getAuthMode()).isEqualTo(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED.name());
        assertThat(response.getCallerType()).isEqualTo(RuntimeAuthCallerType.TRUSTED_BACKEND.name());
        assertThat(response.getSessionId()).isEqualTo("verified-session");
        assertThat(response.getDeploymentId()).isEqualTo("dep-123");
        assertThat(response.getIssuer()).isEqualTo("backend-test");
        assertThat(response.isCompatibilityIdentity()).isFalse();
        assertThat(responseEntity.getHeaders().getFirst("Deprecation")).isNull();
    }

    @Test
    void myAuthContextRequiresVerifiedAuthContext() {
        ChatRuntimeController controller = instantiateController(strictAuthResolver());

        assertThatThrownBy(() -> controller.myAuthContext(new MockHttpServletRequest()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401 UNAUTHORIZED")
            .hasMessageContaining("Verified runtime auth context is required");
    }

    @Test
    void myAuthContextRejectsLegacyQueryParamsOnAuthAwareRoute() {
        ChatRuntimeController controller = instantiateController(strictAuthResolver());

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");
        servletRequest.setParameter("ownerId", "legacy-owner");
        servletRequest.setParameter("sessionId", "legacy-session");

        assertThatThrownBy(() -> controller.myAuthContext(servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("Legacy request identity fields are not allowed on auth-aware runtime endpoint /api/chat/me/auth-context")
            .hasMessageContaining("ownerId, sessionId");
    }

    @Test
    void authContextLegacyCompatibilityUsesRequestIdentity() {
        ChatRuntimeController controller = instantiateController(new RuntimeRequestAuthResolver(new RuntimeAuthProperties()));

        ResponseEntity<RuntimeAuthContextResponse> responseEntity = controller.authContext(
            "legacy-user",
            null,
            "legacy-session",
            new MockHttpServletRequest()
        );
        RuntimeAuthContextResponse response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getSubjectId()).isEqualTo("legacy-user");
        assertThat(response.getAuthMode()).isEqualTo(RuntimeAuthMode.LEGACY_REQUEST_IDENTITY.name());
        assertThat(response.getSessionId()).isEqualTo("legacy-session");
        assertThat(response.isCompatibilityIdentity()).isTrue();
        assertThat(response.getWarnings())
            .containsExactly(RuntimeRequestAuthResolver.WARNING_LEGACY_REQUEST_IDENTITY);
        assertThat(responseEntity.getHeaders().getFirst("Deprecation")).isEqualTo("true");
        assertThat(responseEntity.getHeaders().getFirst("Sunset")).isEqualTo("Wed, 30 Sep 2026 00:00:00 GMT");
        assertThat(responseEntity.getHeaders().getFirst("Link"))
            .isEqualTo("</api/chat/me/auth-context>; rel=\"successor-version\"");
    }

    @Test
    void authContextCanResolveLegacyConversationOwnerIdentity() {
        ChatRuntimeController controller = instantiateController(new RuntimeRequestAuthResolver(new RuntimeAuthProperties()));

        RuntimeAuthContextResponse response = controller.authContext(
            null,
            "legacy-owner",
            null,
            new MockHttpServletRequest()
        ).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getSubjectId()).isEqualTo("legacy-owner");
        assertThat(response.getAuthMode()).isEqualTo(RuntimeAuthMode.LEGACY_REQUEST_IDENTITY.name());
        assertThat(response.isCompatibilityIdentity()).isTrue();
    }

    private RuntimeRequestAuthResolver strictAuthResolver() {
        RuntimeAuthProperties properties = new RuntimeAuthProperties();
        properties.getIngress().setMode(RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED);
        properties.getIngress().setLegacyRequestIdentityEnabled(false);
        properties.getIngress().getTrustedBackend().setApiKeyValue("runtime-secret");
        return new RuntimeRequestAuthResolver(properties);
    }

    private void addVerifiedAuthHeaders(MockHttpServletRequest request, String subjectId, String sessionId) {
        request.addHeader("X-AIFABRIC-RUNTIME-API-KEY", "runtime-secret");
        request.addHeader("X-AIFABRIC-AUTH-SUBJECT-ID", subjectId);
        request.addHeader("X-AIFABRIC-AUTH-SUBJECT-TYPE", RuntimeAuthSubjectType.END_USER.name());
        request.addHeader("X-AIFABRIC-AUTH-MODE", RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED.name());
        request.addHeader("X-AIFABRIC-AUTH-CALLER-TYPE", RuntimeAuthCallerType.TRUSTED_BACKEND.name());
        request.addHeader("X-AIFABRIC-AUTH-SESSION-ID", sessionId);
        request.addHeader("X-AIFABRIC-AUTH-DEPLOYMENT-ID", "dep-123");
        request.addHeader("X-AIFABRIC-AUTH-ISSUER", "backend-test");
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
