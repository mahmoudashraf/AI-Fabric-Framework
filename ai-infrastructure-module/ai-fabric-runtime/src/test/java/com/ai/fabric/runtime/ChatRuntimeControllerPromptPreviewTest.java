package com.ai.fabric.runtime;

import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthIngressMode;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimePublicTokenService;
import com.ai.fabric.runtime.auth.RuntimeAuthSubjectType;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.chat.RuntimeConversationGateway;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.ai.fabric.runtime.config.RuntimeDeploymentPromptConfigService;
import com.ai.fabric.runtime.web.ChatRuntimeController;
import com.ai.fabric.runtime.web.dto.ChatQueryRequest;
import com.ai.fabric.runtime.web.dto.ChatQueryResponse;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatRuntimeControllerPromptPreviewTest {

    private static final List<String> BASE_QUERY_SCOPES = List.of("chat:query");
    private static final List<String> QUERY_WITH_PROMPT_PREVIEW_SCOPES = List.of("chat:query", "chat:prompt-preview");

    @Test
    void previewRequestRequiresPromptPreviewScope() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        ChatRuntimeController controller = controllerFor(orchestrator);

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Preview this");
        request.setPromptPreview(Map.of("systemPrompt", "Use a direct tone."));

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "platform-user-1", "platform-session-1", BASE_QUERY_SCOPES);

        assertThatThrownBy(() -> controller.query(request, servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("403 FORBIDDEN")
            .hasMessageContaining("chat:prompt-preview");
        verify(orchestrator, never()).orchestrate(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.<OrchestrationContext>any()
        );
    }

    @Test
    void previewRequestPropagatesSanitizedOverlayWhenScopeGranted() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        when(orchestrator.orchestrate(eq("Preview this"), org.mockito.ArgumentMatchers.<OrchestrationContext>any())).thenReturn(
            OrchestrationResult.builder()
                .type(OrchestrationResultType.INFORMATION_PROVIDED)
                .success(true)
                .message("Preview answer")
                .build()
        );

        ChatRuntimeController controller = controllerFor(orchestrator);

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Preview this");
        request.setPromptPreview(Map.of(
            "systemPrompt", "Use a direct tone.",
            "answerGenerationPrompt", "Answer in two bullets.",
            "ignored", "should-not-pass-through"
        ));

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "platform-user-1", "platform-session-1", QUERY_WITH_PROMPT_PREVIEW_SCOPES);

        ChatQueryResponse response = controller.query(request, servletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getResult()).isNotNull();

        ArgumentCaptor<OrchestrationContext> contextCaptor = ArgumentCaptor.forClass(OrchestrationContext.class);
        verify(orchestrator).orchestrate(eq("Preview this"), contextCaptor.capture());
        Object rawPromptPreview = contextCaptor.getValue().getMetadata().get("promptPreview");
        assertThat(rawPromptPreview).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, String> promptPreview = (Map<String, String>) rawPromptPreview;
        assertThat(promptPreview).containsEntry("systemPrompt", "Use a direct tone.");
        assertThat(promptPreview).containsEntry("answerGenerationPrompt", "Answer in two bullets.");
        assertThat(promptPreview).doesNotContainKey("ignored");
    }

    @Test
    void deploymentPromptConfigIsAppliedWithoutAdminAuthorization() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        when(orchestrator.orchestrate(eq("Preview this"), org.mockito.ArgumentMatchers.<OrchestrationContext>any())).thenReturn(
            OrchestrationResult.builder()
                .type(OrchestrationResultType.INFORMATION_PROVIDED)
                .success(true)
                .message("Preview answer")
                .build()
        );

        RuntimeDeploymentPromptConfigService promptConfigService = mock(RuntimeDeploymentPromptConfigService.class);
        when(promptConfigService.currentPromptOverlay()).thenReturn(Map.of(
            "systemPrompt", "Use the deployed prompt baseline.",
            "assistantUiPrompt", "Keep replies compact."
        ));

        ChatRuntimeController controller = controllerFor(orchestrator, promptConfigService);

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Preview this");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "platform-user-1", "platform-session-1", BASE_QUERY_SCOPES);

        ChatQueryResponse response = controller.query(request, servletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.isSuccess()).isTrue();

        ArgumentCaptor<OrchestrationContext> contextCaptor = ArgumentCaptor.forClass(OrchestrationContext.class);
        verify(orchestrator).orchestrate(eq("Preview this"), contextCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, String> promptPreview = (Map<String, String>) contextCaptor.getValue().getMetadata().get("promptPreview");
        assertThat(promptPreview).containsEntry("systemPrompt", "Use the deployed prompt baseline.");
        assertThat(promptPreview).containsEntry("assistantUiPrompt", "Keep replies compact.");
    }

    @Test
    void requestPreviewOverridesDeploymentPromptConfig() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        when(orchestrator.orchestrate(eq("Preview this"), org.mockito.ArgumentMatchers.<OrchestrationContext>any())).thenReturn(
            OrchestrationResult.builder()
                .type(OrchestrationResultType.INFORMATION_PROVIDED)
                .success(true)
                .message("Preview answer")
                .build()
        );

        RuntimeDeploymentPromptConfigService promptConfigService = mock(RuntimeDeploymentPromptConfigService.class);
        when(promptConfigService.currentPromptOverlay()).thenReturn(Map.of(
            "systemPrompt", "Use the deployed prompt baseline.",
            "answerGenerationPrompt", "Answer with evidence."
        ));

        ChatRuntimeController controller = controllerFor(orchestrator, promptConfigService);

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Preview this");
        request.setPromptPreview(Map.of(
            "systemPrompt", "Use preview prompt instead.",
            "assistantUiPrompt", "Preview UI prompt."
        ));

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "platform-user-1", "platform-session-1", QUERY_WITH_PROMPT_PREVIEW_SCOPES);

        controller.query(request, servletRequest);

        ArgumentCaptor<OrchestrationContext> contextCaptor = ArgumentCaptor.forClass(OrchestrationContext.class);
        verify(orchestrator).orchestrate(eq("Preview this"), contextCaptor.capture());
        @SuppressWarnings("unchecked")
        Map<String, String> promptPreview = (Map<String, String>) contextCaptor.getValue().getMetadata().get("promptPreview");
        assertThat(promptPreview).containsEntry("systemPrompt", "Use preview prompt instead.");
        assertThat(promptPreview).containsEntry("answerGenerationPrompt", "Answer with evidence.");
        assertThat(promptPreview).containsEntry("assistantUiPrompt", "Preview UI prompt.");
    }

    @Test
    void queryRejectsUnexpectedLegacyIdentityFields() {
        ChatRuntimeController controller = controllerFor(mock(RAGOrchestrator.class), null, strictAuthResolver());

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Explain the failure");
        request.getUnexpectedFields().put("userId", "forged-user");
        request.getUnexpectedFields().put("sessionId", "forged-session");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "platform-user-1", "platform-session-1", BASE_QUERY_SCOPES);

        assertThatThrownBy(() -> controller.query(request, servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("Unexpected request fields are not allowed on verified runtime endpoint /api/chat/me/query")
            .hasMessageContaining("userId, sessionId");
    }

    @Test
    void meQueryUsesVerifiedAuthContextWithoutLegacyBodyIdentity() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        when(orchestrator.orchestrate(eq("Explain the failure"), org.mockito.ArgumentMatchers.<OrchestrationContext>any())).thenReturn(
            OrchestrationResult.builder()
                .type(OrchestrationResultType.INFORMATION_PROVIDED)
                .success(true)
                .message("done")
                .build()
        );

        ChatRuntimeController controller = controllerFor(orchestrator, null, strictAuthResolver());

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Explain the failure");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "platform-user-1", "platform-session-1", BASE_QUERY_SCOPES);

        ResponseEntity<ChatQueryResponse> responseEntity = controller.query(request, servletRequest);
        ChatQueryResponse response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getSessionId()).isEqualTo("platform-session-1");
        assertThat(response.getAuthContext()).isNotNull();
        assertThat(response.getAuthContext().getSubjectId()).isEqualTo("platform-user-1");

        ArgumentCaptor<OrchestrationContext> contextCaptor = ArgumentCaptor.forClass(OrchestrationContext.class);
        verify(orchestrator).orchestrate(eq("Explain the failure"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getUserId()).isEqualTo("platform-user-1");
        assertThat(contextCaptor.getValue().getSessionId()).isEqualTo("platform-session-1");
        assertThat(contextCaptor.getValue().getMetadata())
            .containsEntry("requestedScopes", java.util.List.of("chat:query"));
    }

    @Test
    void meQueryRequiresVerifiedAuthContext() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        ChatRuntimeController controller = controllerFor(orchestrator, null, strictAuthResolver());

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Explain the failure");

        assertThatThrownBy(() -> controller.query(request, new MockHttpServletRequest()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401 UNAUTHORIZED")
            .hasMessageContaining("Verified runtime auth context is required");
    }

    @Test
    void strictModeRejectsLegacyRequestIdentityWithoutVerifiedHeaders() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        ChatRuntimeController controller = controllerFor(orchestrator, null, strictAuthResolver());

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Hello");
        request.getUnexpectedFields().put("userId", "legacy-user");

        assertThatThrownBy(() -> controller.query(request, new MockHttpServletRequest()))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("Unexpected request fields are not allowed on verified runtime endpoint /api/chat/me/query")
            .hasMessageContaining("userId");
    }

    @Test
    void verifiedQueryStillRejectsLegacyIdentityFields() {
        ChatRuntimeController controller = controllerFor(mock(RAGOrchestrator.class), null, strictAuthResolver());

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Hello");
        request.getUnexpectedFields().put("userId", "legacy-user");
        request.getUnexpectedFields().put("sessionId", "legacy-session");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "platform-user-1", "platform-session-1", BASE_QUERY_SCOPES);

        assertThatThrownBy(() -> controller.query(request, servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("Unexpected request fields are not allowed on verified runtime endpoint /api/chat/me/query")
            .hasMessageContaining("userId, sessionId");
    }

    @Test
    void verifiedQueryRejectsLegacyIdentityFieldsEvenWhenValuesMatchAuthContext() {
        ChatRuntimeController controller = controllerFor(mock(RAGOrchestrator.class), null, strictAuthResolver());

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Hello");
        request.getUnexpectedFields().put("userId", "platform-user-1");
        request.getUnexpectedFields().put("sessionId", "platform-session-1");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "platform-user-1", "platform-session-1", BASE_QUERY_SCOPES);

        assertThatThrownBy(() -> controller.query(request, servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("Unexpected request fields are not allowed on verified runtime endpoint /api/chat/me/query")
            .hasMessageContaining("userId, sessionId");
    }

    @Test
    void meQueryUsesPublicAnonymousBearerTokenWithoutRequestIdentity() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        when(orchestrator.orchestrate(eq("Anonymous question"), org.mockito.ArgumentMatchers.<OrchestrationContext>any())).thenReturn(
            OrchestrationResult.builder()
                .type(OrchestrationResultType.INFORMATION_PROVIDED)
                .success(true)
                .message("done")
                .build()
        );

        RuntimeAuthProperties properties = new RuntimeAuthProperties();
        properties.getIngress().setMode(RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED);
        properties.getPublicTokens().setSigningKey("public-secret");
        properties.getPublicTokens().setIssuer("runtime-public-test");
        properties.getPublicTokens().getBootstrap().setEnabled(true);
        RuntimePublicTokenService tokenService = new RuntimePublicTokenService(properties);
        RuntimeRequestAuthResolver authResolver = new RuntimeRequestAuthResolver(properties, tokenService);
        String token = tokenService.issueAnonymousToken("anon-public-session").token();

        ChatRuntimeController controller = controllerFor(orchestrator, null, authResolver);

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Anonymous question");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Authorization", "Bearer " + token);

        ResponseEntity<ChatQueryResponse> responseEntity = controller.query(request, servletRequest);
        ChatQueryResponse response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getSessionId()).isEqualTo("anon-public-session");
        assertThat(response.getAuthContext()).isNotNull();
        assertThat(response.getAuthContext().getSubjectId()).isEqualTo("anon-public-session");
        assertThat(response.getAuthContext().getSubjectType()).isEqualTo(RuntimeAuthSubjectType.ANONYMOUS_SESSION.name());
        assertThat(response.getAuthContext().getAuthMode()).isEqualTo(RuntimeAuthMode.PUBLIC_RUNTIME_ANONYMOUS.name());
        assertThat(response.getAuthContext().getSessionId()).isEqualTo("anon-public-session");
        assertThat(responseEntity.getHeaders().getFirst("X-AIFABRIC-RUNTIME-AUTH-MODE"))
            .isEqualTo(RuntimeAuthMode.PUBLIC_RUNTIME_ANONYMOUS.name());

        ArgumentCaptor<OrchestrationContext> contextCaptor = ArgumentCaptor.forClass(OrchestrationContext.class);
        verify(orchestrator).orchestrate(eq("Anonymous question"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getUserId()).isNull();
        assertThat(contextCaptor.getValue().getSessionId()).isEqualTo("anon-public-session");
        assertThat(contextCaptor.getValue().getMetadata())
            .containsEntry("authMode", RuntimeAuthMode.PUBLIC_RUNTIME_ANONYMOUS.name())
            .containsEntry("subjectType", RuntimeAuthSubjectType.ANONYMOUS_SESSION.name())
            .containsEntry("authIssuer", "runtime-public-test")
            .containsEntry("requestedScopes", java.util.List.of("chat:query"));
    }

    @Test
    void meQueryUsesPublicAuthenticatedBearerTokenWithoutLegacyRequestIdentity() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        when(orchestrator.orchestrate(eq("Authenticated question"), org.mockito.ArgumentMatchers.<OrchestrationContext>any())).thenReturn(
            OrchestrationResult.builder()
                .type(OrchestrationResultType.INFORMATION_PROVIDED)
                .success(true)
                .message("done")
                .build()
        );

        RuntimeAuthProperties properties = new RuntimeAuthProperties();
        properties.getIngress().setMode(RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED);
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
            List.of("chat:query", "chat:conversations"),
            "shopify-app",
            List.of("storefront-chat")
        ).token();

        ChatRuntimeController controller = controllerFor(orchestrator, null, authResolver);

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Authenticated question");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("Authorization", "Bearer " + token);

        ChatQueryResponse response = controller.query(request, servletRequest).getBody();

        assertThat(response).isNotNull();
        assertThat(response.getSessionId()).isEqualTo("session-public-authenticated");
        assertThat(response.getAuthContext()).isNotNull();
        assertThat(response.getAuthContext().getSubjectId()).isEqualTo("customer-123");
        assertThat(response.getAuthContext().getSubjectType()).isEqualTo(RuntimeAuthSubjectType.END_USER.name());
        assertThat(response.getAuthContext().getAuthMode()).isEqualTo(RuntimeAuthMode.PUBLIC_RUNTIME_AUTHENTICATED.name());
        assertThat(response.getAuthContext().getSessionId()).isEqualTo("session-public-authenticated");

        ArgumentCaptor<OrchestrationContext> contextCaptor = ArgumentCaptor.forClass(OrchestrationContext.class);
        verify(orchestrator).orchestrate(eq("Authenticated question"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getUserId()).isEqualTo("customer-123");
        assertThat(contextCaptor.getValue().getSessionId()).isEqualTo("session-public-authenticated");
        assertThat(contextCaptor.getValue().getMetadata())
            .containsEntry("authMode", RuntimeAuthMode.PUBLIC_RUNTIME_AUTHENTICATED.name())
            .containsEntry("subjectType", RuntimeAuthSubjectType.END_USER.name())
            .containsEntry("authIssuer", "shopify-app")
            .containsEntry("requestedScopes", java.util.List.of("chat:query"));
    }

    private ChatRuntimeController controllerFor(RAGOrchestrator orchestrator) {
        return controllerFor(orchestrator, null, defaultAuthResolver());
    }

    private ChatRuntimeController controllerFor(RAGOrchestrator orchestrator,
                                                RuntimeDeploymentPromptConfigService promptConfigService) {
        return controllerFor(orchestrator, promptConfigService, defaultAuthResolver());
    }

    private ChatRuntimeController controllerFor(RAGOrchestrator orchestrator,
                                                RuntimeDeploymentPromptConfigService promptConfigService,
                                                RuntimeRequestAuthResolver authResolver) {
        return instantiateController(
            provider(orchestrator),
            mock(RuntimeConversationGateway.class),
            provider(null),
            provider(null),
            provider(promptConfigService),
            authResolver
        );
    }

    private ChatRuntimeController instantiateController(ObjectProvider<?> orchestratorProvider,
                                                        RuntimeConversationGateway conversationGateway,
                                                        ObjectProvider<?> aiCoreServiceProvider,
                                                        ObjectProvider<?> aiActionRegistryProvider,
                                                        ObjectProvider<?> promptConfigProvider,
                                                        RuntimeRequestAuthResolver authResolver) {
        try {
            Constructor<?> constructor = ChatRuntimeController.class.getDeclaredConstructors()[0];
            constructor.setAccessible(true);
            return (ChatRuntimeController) constructor.newInstance(
                orchestratorProvider,
                conversationGateway,
                aiCoreServiceProvider,
                aiActionRegistryProvider,
                promptConfigProvider,
                authResolver
            );
        } catch (ReflectiveOperationException ex) {
            throw new RuntimeException(ex);
        }
    }

    private RuntimeRequestAuthResolver defaultAuthResolver() {
        RuntimeAuthProperties properties = authProperties();
        return new RuntimeRequestAuthResolver(properties);
    }

    private RuntimeRequestAuthResolver strictAuthResolver() {
        RuntimeAuthProperties properties = authProperties();
        return new RuntimeRequestAuthResolver(properties);
    }

    private void addVerifiedAuthHeaders(MockHttpServletRequest request,
                                        String subjectId,
                                        String sessionId,
                                        List<String> scopes) {
        RuntimePrivateAssertionTestSupport.addPrivateRuntimeHeaders(
            request,
            authProperties(),
            RuntimeAuthContext.builder()
                .subjectId(subjectId)
                .subjectType(RuntimeAuthSubjectType.INTERNAL_PLATFORM_USER)
                .authMode(RuntimeAuthMode.PLATFORM_PROXY_SESSION)
                .callerType(RuntimeAuthCallerType.PLATFORM_PROXY)
                .sessionId(sessionId)
                .deploymentId("dep-123")
                .issuer("platform-backend")
                .audiences(List.of("dep-123"))
                .grantedScopes(scopes)
                .expiresAt(Instant.now().plusSeconds(300))
                .build()
        );
    }

    private RuntimeAuthProperties authProperties() {
        RuntimeAuthProperties properties = RuntimePrivateAssertionTestSupport.strictPrivateRuntimeProperties();
        properties.getIngress().setAcceptedIssuers(List.of("platform-backend"));
        properties.getIngress().setAcceptedAudiences(List.of("dep-123"));
        return properties;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ObjectProvider provider(Object value) {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
