package com.ai.fabric.runtime;

import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthIngressMode;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.ai.fabric.runtime.web.ChatRuntimeController;
import com.ai.fabric.runtime.web.dto.ConversationResponse;
import com.ai.fabric.runtime.web.dto.ConversationSummaryResponse;
import com.ai.infrastructure.chat.domain.ChatSession;
import com.ai.infrastructure.chat.domain.SessionStatus;
import com.ai.infrastructure.chat.service.ChatSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
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
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        when(chatSessionService.getSession("chat-1", "verified-user"))
            .thenReturn(session("chat-1", "verified-user"));

        ChatRuntimeController controller = instantiateController(
            provider(chatSessionService),
            strictAuthResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        ConversationResponse response = controller
            .getConversation("chat-1", null, null, servletRequest)
            .getBody();

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo("chat-1");
        assertThat(response.getOwnerId()).isEqualTo("verified-user");
        assertThat(response.getAuthContext()).isNotNull();
        assertThat(response.getAuthContext().getSubjectId()).isEqualTo("verified-user");
        assertThat(response.getAuthContext().getSubjectType()).isEqualTo(RuntimeAuthSubjectType.END_USER.name());
        assertThat(response.getAuthContext().getAuthMode()).isEqualTo(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED.name());
        assertThat(response.getAuthContext().getSessionId()).isEqualTo("verified-session");

        verify(chatSessionService).getSession("chat-1", "verified-user");
    }

    @Test
    void listConversationsUsesVerifiedAuthContextOwnerWithoutQueryIdentity() {
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        when(chatSessionService.getUserConversations("verified-user"))
            .thenReturn(List.of(session("chat-1", "verified-user")));

        ChatRuntimeController controller = instantiateController(
            provider(chatSessionService),
            strictAuthResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        List<ConversationSummaryResponse> response = controller
            .listConversations(null, null, servletRequest)
            .getBody();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getId()).isEqualTo("chat-1");
        assertThat(response.getFirst().getOwnerId()).isEqualTo("verified-user");
        assertThat(response.getFirst().getAuthContext()).isNotNull();
        assertThat(response.getFirst().getAuthContext().getSubjectId()).isEqualTo("verified-user");
        assertThat(response.getFirst().getAuthContext().getSubjectType()).isEqualTo(RuntimeAuthSubjectType.END_USER.name());
        assertThat(response.getFirst().getAuthContext().getAuthMode()).isEqualTo(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED.name());
        assertThat(response.getFirst().getAuthContext().getSessionId()).isEqualTo("verified-session");

        verify(chatSessionService).getUserConversations("verified-user");
    }

    @Test
    void deleteConversationUsesVerifiedAuthContextOwnerWithoutQueryIdentity() {
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        ChatRuntimeController controller = instantiateController(
            provider(chatSessionService),
            strictAuthResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        controller.deleteConversation("chat-1", null, null, servletRequest);

        verify(chatSessionService).deleteConversation("chat-1", "verified-user");
    }

    @Test
    void strictConversationModeRejectsLegacyOwnerOnlyRequests() {
        ChatRuntimeController controller = instantiateController(
            provider(mock(ChatSessionService.class)),
            strictAuthResolver()
        );

        assertThatThrownBy(() -> controller.listConversations(null, "legacy-owner", new MockHttpServletRequest()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401 UNAUTHORIZED")
            .hasMessageContaining("Verified runtime auth context is required");
    }

    @Test
    void legacyConversationCompatibilityStillUsesOwnerIdWhenEnabled() {
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        when(chatSessionService.getUserConversations("legacy-owner"))
            .thenReturn(List.of(session("chat-1", "legacy-owner")));

        ChatRuntimeController controller = instantiateController(
            provider(chatSessionService),
            new RuntimeRequestAuthResolver(new RuntimeAuthProperties())
        );

        List<ConversationSummaryResponse> response = controller
            .listConversations(null, "legacy-owner", new MockHttpServletRequest())
            .getBody();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().getOwnerId()).isEqualTo("legacy-owner");
        assertThat(response.getFirst().getAuthContext()).isNotNull();
        assertThat(response.getFirst().getAuthContext().getSubjectId()).isEqualTo("legacy-owner");
        assertThat(response.getFirst().getAuthContext().getSubjectType()).isEqualTo(RuntimeAuthSubjectType.END_USER.name());
        assertThat(response.getFirst().getAuthContext().getAuthMode()).isEqualTo(RuntimeAuthMode.LEGACY_REQUEST_IDENTITY.name());
        assertThat(response.getFirst().getAuthContext().isCompatibilityIdentity()).isTrue();
        assertThat(response.getFirst().getAuthContext().getWarnings())
            .containsExactly(RuntimeRequestAuthResolver.WARNING_LEGACY_REQUEST_IDENTITY);

        verify(chatSessionService).getUserConversations("legacy-owner");
    }

    @Test
    void strictConversationModeIgnoresConflictingLegacyOwnerQueryWhenVerifiedIdentityExists() {
        ChatSessionService chatSessionService = mock(ChatSessionService.class);
        when(chatSessionService.getSession("chat-1", "verified-user"))
            .thenReturn(session("chat-1", "verified-user"));
        ChatRuntimeController controller = instantiateController(
            provider(chatSessionService),
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
        verify(chatSessionService).getSession("chat-1", "verified-user");
    }

    @Test
    void strictConversationConflictModeRejectsConflictingLegacyOwnerQueryWhenVerifiedIdentityExists() {
        ChatRuntimeController controller = instantiateController(
            provider(mock(ChatSessionService.class)),
            strictConflictResolver()
        );

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "verified-user", "verified-session");

        assertThatThrownBy(() -> controller.getConversation("chat-1", "legacy-user", "legacy-owner", servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("Request userId conflicts with verified runtime auth context");
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

    private ChatSession session(String id, String ownerId) {
        LocalDateTime now = LocalDateTime.of(2026, 4, 6, 12, 0);
        return ChatSession.builder()
            .id(id)
            .ownerId(ownerId)
            .status(SessionStatus.ACTIVE)
            .createdAt(now)
            .lastInteractionAt(now)
            .build();
    }

    private ChatRuntimeController instantiateController(ObjectProvider<?> chatSessionServiceProvider,
                                                        RuntimeRequestAuthResolver authResolver) {
        try {
            Constructor<?> constructor = ChatRuntimeController.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return (ChatRuntimeController) constructor.newInstance(
                provider(null),
                chatSessionServiceProvider,
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
