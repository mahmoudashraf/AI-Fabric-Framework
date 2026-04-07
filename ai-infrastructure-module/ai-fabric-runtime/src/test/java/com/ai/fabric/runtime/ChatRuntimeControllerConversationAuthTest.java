package com.ai.fabric.runtime;

import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthIngressMode;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimePublicTokenService;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.chat.RuntimeConversationGateway;
import com.ai.fabric.runtime.chat.RuntimeConversationRecord;
import com.ai.fabric.runtime.chat.RuntimeConversationTurnRecord;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.ai.fabric.runtime.web.ChatRuntimeController;
import com.ai.fabric.runtime.web.dto.ConversationResponse;
import com.ai.fabric.runtime.web.dto.ConversationSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRuntimeControllerConversationAuthTest {

    @Test
    void getConversationUsesVerifiedAuthContextOwnerWithoutQueryIdentity() {
        RuntimeConversationGateway conversationGateway = mock(RuntimeConversationGateway.class);
        when(conversationGateway.isAvailable()).thenReturn(true);
        when(conversationGateway.getConversation("chat-1", "verified-user"))
            .thenReturn(session("chat-1", "verified-user"));

        ChatRuntimeController controller = instantiateController(
            conversationGateway,
            strictAuthResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        ResponseEntity<ConversationResponse> responseEntity = controller.getConversation("chat-1", null, null, servletRequest);
        ConversationResponse response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("chat-1");
        assertThat(response.getOwnerId()).isEqualTo("verified-user");
        assertThat(response.getAuthContext()).isNotNull();
        assertThat(response.getAuthContext().getSubjectId()).isEqualTo("verified-user");
        assertThat(response.getAuthContext().getSubjectType()).isEqualTo(RuntimeAuthSubjectType.END_USER.name());
        assertThat(response.getAuthContext().getAuthMode()).isEqualTo(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED.name());
        assertThat(response.getAuthContext().getCallerType()).isEqualTo(RuntimeAuthCallerType.TRUSTED_BACKEND.name());
        assertThat(response.getAuthContext().getSessionId()).isEqualTo("verified-session");
        assertThat(response.getAuthContext().getDeploymentId()).isEqualTo("dep-123");
        assertThat(response.getAuthContext().getIssuer()).isEqualTo("backend-test");
        assertThat(responseEntity.getHeaders().getFirst("X-AIFABRIC-RUNTIME-AUTH-MODE"))
            .isEqualTo(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED.name());
        assertThat(responseEntity.getHeaders().getFirst("X-AIFABRIC-RUNTIME-COMPATIBILITY-IDENTITY"))
            .isEqualTo("false");
        assertThat(responseEntity.getHeaders().getFirst("Deprecation")).isEqualTo("true");
        assertThat(responseEntity.getHeaders().getFirst("Sunset")).isEqualTo("Wed, 30 Sep 2026 00:00:00 GMT");
        assertThat(responseEntity.getHeaders().getFirst("Link"))
            .isEqualTo("</api/chat/me/conversations/{conversationId}>; rel=\"successor-version\"");

        verify(conversationGateway).getConversation("chat-1", "verified-user");
    }

    @Test
    void listConversationsUsesVerifiedAuthContextOwnerWithoutQueryIdentity() {
        RuntimeConversationGateway conversationGateway = mock(RuntimeConversationGateway.class);
        when(conversationGateway.isAvailable()).thenReturn(true);
        when(conversationGateway.listConversations("verified-user"))
            .thenReturn(List.of(session("chat-1", "verified-user")));

        ChatRuntimeController controller = instantiateController(
            conversationGateway,
            strictAuthResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        ResponseEntity<List<ConversationSummaryResponse>> responseEntity = controller
            .listConversations(null, null, servletRequest);
        List<ConversationSummaryResponse> response = responseEntity.getBody();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getId()).isEqualTo("chat-1");
        assertThat(response.getFirst().getOwnerId()).isEqualTo("verified-user");
        assertThat(response.getFirst().getAuthContext()).isNotNull();
        assertThat(response.getFirst().getAuthContext().getSubjectId()).isEqualTo("verified-user");
        assertThat(response.getFirst().getAuthContext().getSubjectType()).isEqualTo(RuntimeAuthSubjectType.END_USER.name());
        assertThat(response.getFirst().getAuthContext().getAuthMode()).isEqualTo(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED.name());
        assertThat(response.getFirst().getAuthContext().getCallerType()).isEqualTo(RuntimeAuthCallerType.TRUSTED_BACKEND.name());
        assertThat(response.getFirst().getAuthContext().getSessionId()).isEqualTo("verified-session");
        assertThat(response.getFirst().getAuthContext().getDeploymentId()).isEqualTo("dep-123");
        assertThat(response.getFirst().getAuthContext().getIssuer()).isEqualTo("backend-test");
        assertThat(responseEntity.getHeaders().getFirst("Deprecation")).isEqualTo("true");
        assertThat(responseEntity.getHeaders().getFirst("Sunset")).isEqualTo("Wed, 30 Sep 2026 00:00:00 GMT");
        assertThat(responseEntity.getHeaders().getFirst("Link"))
            .isEqualTo("</api/chat/me/conversations>; rel=\"successor-version\"");

        verify(conversationGateway).listConversations("verified-user");
    }

    @Test
    void deleteConversationUsesVerifiedAuthContextOwnerWithoutQueryIdentity() {
        RuntimeConversationGateway conversationGateway = mock(RuntimeConversationGateway.class);
        ChatRuntimeController controller = instantiateController(
            conversationGateway,
            strictAuthResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        ResponseEntity<Void> responseEntity = controller.deleteConversation("chat-1", null, null, servletRequest);

        assertThat(responseEntity.getHeaders().getFirst("Deprecation")).isEqualTo("true");
        assertThat(responseEntity.getHeaders().getFirst("Sunset")).isEqualTo("Wed, 30 Sep 2026 00:00:00 GMT");
        assertThat(responseEntity.getHeaders().getFirst("Link"))
            .isEqualTo("</api/chat/me/conversations/{conversationId}>; rel=\"successor-version\"");

        verify(conversationGateway).deleteConversation("chat-1", "verified-user");
    }

    @Test
    void listMyConversationsUsesVerifiedAuthContextOwnerWithoutLegacyQuerySurface() {
        RuntimeConversationGateway conversationGateway = mock(RuntimeConversationGateway.class);
        when(conversationGateway.isAvailable()).thenReturn(true);
        when(conversationGateway.listConversations("verified-user"))
            .thenReturn(List.of(session("chat-1", "verified-user")));

        ChatRuntimeController controller = instantiateController(
            conversationGateway,
            strictAuthResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        ResponseEntity<List<ConversationSummaryResponse>> responseEntity = controller
            .listMyConversations(servletRequest);
        List<ConversationSummaryResponse> response = responseEntity.getBody();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getOwnerId()).isNull();
        assertThat(response.getFirst().getAuthContext()).isNotNull();
        assertThat(response.getFirst().getAuthContext().getSubjectId()).isEqualTo("verified-user");
        assertThat(responseEntity.getHeaders().getFirst("Deprecation")).isNull();
        verify(conversationGateway).listConversations("verified-user");
    }

    @Test
    void listMyConversationsRejectsLegacyQueryParamsOnAuthAwareRoute() {
        ChatRuntimeController controller = instantiateController(
            mock(RuntimeConversationGateway.class),
            strictAuthResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");
        servletRequest.setParameter("ownerId", "legacy-owner");

        assertThatThrownBy(() -> controller.listMyConversations(servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("Legacy request identity fields are not allowed on auth-aware runtime endpoint /api/chat/me/conversations")
            .hasMessageContaining("ownerId");
    }

    @Test
    void getMyConversationUsesVerifiedAuthContextOwnerWithoutLegacyQuerySurface() {
        RuntimeConversationGateway conversationGateway = mock(RuntimeConversationGateway.class);
        when(conversationGateway.isAvailable()).thenReturn(true);
        when(conversationGateway.getConversation("chat-1", "verified-user"))
            .thenReturn(session("chat-1", "verified-user"));

        ChatRuntimeController controller = instantiateController(
            conversationGateway,
            strictAuthResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        ResponseEntity<ConversationResponse> responseEntity = controller
            .getMyConversation("chat-1", servletRequest);
        ConversationResponse response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getOwnerId()).isNull();
        assertThat(response.getAuthContext()).isNotNull();
        assertThat(response.getAuthContext().getSubjectId()).isEqualTo("verified-user");
        assertThat(responseEntity.getHeaders().getFirst("Deprecation")).isNull();
        verify(conversationGateway).getConversation("chat-1", "verified-user");
    }

    @Test
    void getMyConversationRejectsLegacyQueryParamsOnAuthAwareRoute() {
        ChatRuntimeController controller = instantiateController(
            mock(RuntimeConversationGateway.class),
            strictAuthResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");
        servletRequest.setParameter("userId", "legacy-user");
        servletRequest.setParameter("sessionId", "legacy-session");

        assertThatThrownBy(() -> controller.getMyConversation("chat-1", servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("Legacy request identity fields are not allowed on auth-aware runtime endpoint /api/chat/me/conversations/{conversationId}")
            .hasMessageContaining("userId, sessionId");
    }

    @Test
    void deleteMyConversationUsesVerifiedAuthContextOwnerWithoutLegacyQuerySurface() {
        RuntimeConversationGateway conversationGateway = mock(RuntimeConversationGateway.class);
        ChatRuntimeController controller = instantiateController(
            conversationGateway,
            strictAuthResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        ResponseEntity<Void> responseEntity = controller.deleteMyConversation("chat-1", servletRequest);

        assertThat(responseEntity.getHeaders().getFirst("Deprecation")).isNull();

        verify(conversationGateway).deleteConversation("chat-1", "verified-user");
    }

    @Test
    void authAwareConversationEndpointsRequireVerifiedAuthContext() {
        ChatRuntimeController controller = instantiateController(
            mock(RuntimeConversationGateway.class),
            strictAuthResolver()
        );

        assertThatThrownBy(() -> controller.listMyConversations(new MockHttpServletRequest()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401 UNAUTHORIZED")
            .hasMessageContaining("Verified runtime auth context is required");
    }

    @Test
    void strictConversationModeRejectsLegacyOwnerOnlyRequests() {
        ChatRuntimeController controller = instantiateController(
            mock(RuntimeConversationGateway.class),
            strictAuthResolver()
        );

        assertThatThrownBy(() -> controller.listConversations(null, "legacy-owner", new MockHttpServletRequest()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401 UNAUTHORIZED")
            .hasMessageContaining("Verified runtime auth context is required");
    }

    @Test
    void legacyConversationCompatibilityStillUsesOwnerIdWhenEnabled() {
        RuntimeConversationGateway conversationGateway = mock(RuntimeConversationGateway.class);
        when(conversationGateway.isAvailable()).thenReturn(true);
        when(conversationGateway.listConversations("legacy-owner"))
            .thenReturn(List.of(session("chat-1", "legacy-owner")));

        ChatRuntimeController controller = instantiateController(
            conversationGateway,
            new RuntimeRequestAuthResolver(new RuntimeAuthProperties())
        );

        ResponseEntity<List<ConversationSummaryResponse>> responseEntity = controller
            .listConversations(null, "legacy-owner", new MockHttpServletRequest());
        List<ConversationSummaryResponse> response = responseEntity.getBody();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getOwnerId()).isEqualTo("legacy-owner");
        assertThat(response.getFirst().getAuthContext()).isNotNull();
        assertThat(response.getFirst().getAuthContext().getSubjectId()).isEqualTo("legacy-owner");
        assertThat(response.getFirst().getAuthContext().getSubjectType()).isEqualTo(RuntimeAuthSubjectType.END_USER.name());
        assertThat(response.getFirst().getAuthContext().getAuthMode()).isEqualTo(RuntimeAuthMode.LEGACY_REQUEST_IDENTITY.name());
        assertThat(response.getFirst().getAuthContext().isCompatibilityIdentity()).isTrue();
        assertThat(response.getFirst().getAuthContext().getWarnings())
            .containsExactly(RuntimeRequestAuthResolver.WARNING_LEGACY_REQUEST_IDENTITY);
        assertThat(responseEntity.getHeaders().getFirst("X-AIFABRIC-RUNTIME-AUTH-MODE"))
            .isEqualTo(RuntimeAuthMode.LEGACY_REQUEST_IDENTITY.name());
        assertThat(responseEntity.getHeaders().getFirst("X-AIFABRIC-RUNTIME-COMPATIBILITY-IDENTITY"))
            .isEqualTo("true");
        assertThat(responseEntity.getHeaders().getFirst("Deprecation")).isEqualTo("true");
        assertThat(responseEntity.getHeaders().getFirst("Sunset")).isEqualTo("Wed, 30 Sep 2026 00:00:00 GMT");
        assertThat(responseEntity.getHeaders().getFirst("Link"))
            .isEqualTo("</api/chat/me/conversations>; rel=\"successor-version\"");
        assertThat(responseEntity.getHeaders().getFirst("X-AIFABRIC-RUNTIME-AUTH-WARNINGS"))
            .isEqualTo(RuntimeRequestAuthResolver.WARNING_LEGACY_REQUEST_IDENTITY);

        verify(conversationGateway).listConversations("legacy-owner");
    }

    @Test
    void strictConversationModeIgnoresConflictingLegacyOwnerQueryWhenVerifiedIdentityExists() {
        RuntimeConversationGateway conversationGateway = mock(RuntimeConversationGateway.class);
        when(conversationGateway.isAvailable()).thenReturn(true);
        when(conversationGateway.getConversation("chat-1", "verified-user"))
            .thenReturn(session("chat-1", "verified-user"));
        ChatRuntimeController controller = instantiateController(
            conversationGateway,
            strictAuthResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        ConversationResponse response = controller
            .getConversation("chat-1", "legacy-user", "legacy-owner", servletRequest)
            .getBody();

        assertThat(response).isNotNull();
        assertThat(response.getAuthContext()).isNotNull();
        assertThat(response.getAuthContext().isCompatibilityIdentity()).isFalse();
        assertThat(response.getAuthContext().getWarnings())
            .containsExactlyInAnyOrder(
                RuntimeRequestAuthResolver.WARNING_REQUEST_USER_ID_CONFLICT,
                RuntimeRequestAuthResolver.WARNING_REQUEST_OWNER_ID_CONFLICT
            );
        verify(conversationGateway).getConversation("chat-1", "verified-user");
    }

    @Test
    void strictConversationConflictModeRejectsConflictingLegacyOwnerQueryWhenVerifiedIdentityExists() {
        ChatRuntimeController controller = instantiateController(
            mock(RuntimeConversationGateway.class),
            strictConflictResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        assertThatThrownBy(() -> controller.getConversation("chat-1", "legacy-user", "legacy-owner", servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("Request userId conflicts with verified runtime auth context");
    }

    @Test
    void listConversationsRejectsPublicAuthenticatedTokenWithoutConversationScope() {
        RuntimeConversationGateway conversationGateway = mock(RuntimeConversationGateway.class);
        when(conversationGateway.isAvailable()).thenReturn(true);

        RuntimeAuthProperties properties = new RuntimeAuthProperties();
        properties.getIngress().setMode(RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED);
        properties.getIngress().setLegacyRequestIdentityEnabled(false);
        properties.getPublicTokens().setSigningKey("public-secret");
        properties.getPublicTokens().setIssuer("runtime-public-test");
        properties.getPublicTokens().setAcceptedIssuers(List.of("runtime-public-test", "shopify-app"));
        properties.getPublicTokens().setAcceptedAudiences(List.of("storefront-chat"));
        properties.getPublicTokens().setDefaultAudience("storefront-chat");
        RuntimePublicTokenService tokenService = new RuntimePublicTokenService(properties);
        RuntimeRequestAuthResolver authResolver = new RuntimeRequestAuthResolver(properties, tokenService);
        String token = tokenService.issueAuthenticatedToken(
            "customer-123",
            RuntimeAuthSubjectType.END_USER,
            "session-public-authenticated",
            "dep-public",
            "cus-public",
            "ten-public",
            List.of("chat:query"),
            "shopify-app",
            List.of("storefront-chat")
        ).token();

        ChatRuntimeController controller = instantiateController(conversationGateway, authResolver);
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Authorization", "Bearer " + token);

        assertThatThrownBy(() -> controller.listConversations(null, null, servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN")
            .hasMessageContaining("chat:conversations");
    }

    private RuntimeRequestAuthResolver strictAuthResolver() {
        RuntimeAuthProperties properties = new RuntimeAuthProperties();
        properties.getIngress().setMode(RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED);
        properties.getIngress().setLegacyRequestIdentityEnabled(false);
        properties.getIngress().getTrustedBackend().setApiKeyValue("runtime-secret");
        return new RuntimeRequestAuthResolver(properties);
    }

    private RuntimeRequestAuthResolver strictConflictResolver() {
        RuntimeAuthProperties properties = new RuntimeAuthProperties();
        properties.getIngress().setMode(RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED);
        properties.getIngress().setLegacyRequestIdentityEnabled(false);
        properties.getIngress().setRejectConflictingRequestIdentity(true);
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

    private RuntimeConversationRecord session(String id, String ownerId) {
        LocalDateTime now = LocalDateTime.of(2026, 4, 6, 12, 0);
        return new RuntimeConversationRecord(
            id,
            ownerId,
            "ACTIVE",
            now,
            now,
            List.of(new RuntimeConversationTurnRecord(now, "hello", "world"))
        );
    }

    private ChatRuntimeController instantiateController(RuntimeConversationGateway conversationGateway,
                                                        RuntimeRequestAuthResolver authResolver) {
        try {
            Constructor<?> constructor = ChatRuntimeController.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return (ChatRuntimeController) constructor.newInstance(
                provider(null),
                conversationGateway,
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
