package com.ai.fabric.runtime;

import com.ai.fabric.runtime.auth.RuntimeAuthCallerType;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Constructor;
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

    @Test
    void previewRequestRequiresAdminAuthorization() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        ChatRuntimeController controller = controllerFor(orchestrator);

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Preview this");
        request.setPromptPreview(Map.of("systemPrompt", "Use a direct tone."));

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();

        var response = controller.query(request, servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(403));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isFalse();
        assertThat(response.getBody().getMessage()).isEqualTo("Prompt preview requires admin authorization.");
        verify(orchestrator, never()).orchestrate(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.<OrchestrationContext>any()
        );
    }

    @Test
    void previewRequestRequiresConfiguredAdminKey() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        ChatRuntimeController controller = controllerFor(orchestrator);
        ReflectionTestUtils.setField(controller, "adminApiKey", "");

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Preview this");
        request.setPromptPreview(Map.of("systemPrompt", "Use a direct tone."));

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader("X-ADMIN-API-KEY", "preview-secret");

        var response = controller.query(request, servletRequest);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(403));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Prompt preview requires admin authorization.");
        verify(orchestrator, never()).orchestrate(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.<OrchestrationContext>any()
        );
    }

    @Test
    void previewRequestPropagatesSanitizedOverlayWhenAdminAuthorized() {
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
        servletRequest.addHeader("X-ADMIN-API-KEY", "preview-secret");

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

        ChatQueryResponse response = controller.query(request, new MockHttpServletRequest()).getBody();

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
        servletRequest.addHeader("X-ADMIN-API-KEY", "preview-secret");

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
    void queryPrefersVerifiedAuthContextHeadersOverRequestIdentity() {
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
        request.setUserId("forged-user");
        request.setSessionId("forged-session");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "platform-user-1", "platform-session-1");

        ResponseEntity<ChatQueryResponse> responseEntity = controller.query(request, servletRequest);
        ChatQueryResponse response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo("platform-user-1");
        assertThat(response.getSessionId()).isEqualTo("platform-session-1");
        assertThat(response.getAuthContext()).isNotNull();
        assertThat(response.getAuthContext().getSubjectId()).isEqualTo("platform-user-1");
        assertThat(response.getAuthContext().getSubjectType()).isEqualTo(RuntimeAuthSubjectType.INTERNAL_PLATFORM_USER.name());
        assertThat(response.getAuthContext().getAuthMode()).isEqualTo(RuntimeAuthMode.PLATFORM_PROXY_SESSION.name());
        assertThat(response.getAuthContext().getSessionId()).isEqualTo("platform-session-1");
        assertThat(responseEntity.getHeaders().getFirst("X-AIFABRIC-RUNTIME-AUTH-MODE"))
            .isEqualTo(RuntimeAuthMode.PLATFORM_PROXY_SESSION.name());
        assertThat(responseEntity.getHeaders().getFirst("X-AIFABRIC-RUNTIME-CALLER-TYPE"))
            .isEqualTo(RuntimeAuthCallerType.PLATFORM_PROXY.name());
        assertThat(responseEntity.getHeaders().getFirst("X-AIFABRIC-RUNTIME-COMPATIBILITY-IDENTITY"))
            .isEqualTo("false");
        assertThat(responseEntity.getHeaders().getFirst("Deprecation")).isEqualTo("true");
        assertThat(responseEntity.getHeaders().getFirst("Sunset")).isEqualTo("Wed, 30 Sep 2026 00:00:00 GMT");
        assertThat(responseEntity.getHeaders().getFirst("Link"))
            .isEqualTo("</api/chat/me/query>; rel=\"successor-version\"");
        assertThat(responseEntity.getHeaders().getFirst("X-AIFABRIC-RUNTIME-AUTH-WARNINGS"))
            .isEqualTo(RuntimeRequestAuthResolver.WARNING_REQUEST_USER_ID_CONFLICT + "," + RuntimeRequestAuthResolver.WARNING_REQUEST_SESSION_ID_CONFLICT);

        ArgumentCaptor<OrchestrationContext> contextCaptor = ArgumentCaptor.forClass(OrchestrationContext.class);
        verify(orchestrator).orchestrate(eq("Explain the failure"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getUserId()).isEqualTo("platform-user-1");
        assertThat(contextCaptor.getValue().getSessionId()).isEqualTo("platform-session-1");
        assertThat(contextCaptor.getValue().getMetadata())
            .containsEntry("authMode", RuntimeAuthMode.PLATFORM_PROXY_SESSION.name())
            .containsEntry("subjectType", RuntimeAuthSubjectType.INTERNAL_PLATFORM_USER.name())
            .containsEntry("deploymentId", "dep-123")
            .containsEntry("requestedScopes", java.util.List.of("chat:query"));
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
        addVerifiedAuthHeaders(servletRequest, "platform-user-1", "platform-session-1");

        ResponseEntity<ChatQueryResponse> responseEntity = controller.queryMe(request, servletRequest);
        ChatQueryResponse response = responseEntity.getBody();

        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isNull();
        assertThat(response.getSessionId()).isNull();
        assertThat(response.getAuthContext()).isNotNull();
        assertThat(response.getAuthContext().getSubjectId()).isEqualTo("platform-user-1");
        assertThat(response.getAuthContext().isCompatibilityIdentity()).isFalse();
        assertThat(responseEntity.getHeaders().getFirst("X-AIFABRIC-RUNTIME-COMPATIBILITY-IDENTITY"))
            .isEqualTo("false");
        assertThat(responseEntity.getHeaders().getFirst("Deprecation")).isNull();

        ArgumentCaptor<OrchestrationContext> contextCaptor = ArgumentCaptor.forClass(OrchestrationContext.class);
        verify(orchestrator).orchestrate(eq("Explain the failure"), contextCaptor.capture());
        assertThat(contextCaptor.getValue().getUserId()).isEqualTo("platform-user-1");
        assertThat(contextCaptor.getValue().getSessionId()).isEqualTo("platform-session-1");
        assertThat(contextCaptor.getValue().getMetadata())
            .containsEntry("requestedScopes", java.util.List.of("chat:query"));
    }

    @Test
    void meQueryRejectsLegacyBodyIdentityFields() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        ChatRuntimeController controller = controllerFor(orchestrator, null, strictAuthResolver());

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Explain the failure");
        request.setUserId("legacy-user");
        request.setSessionId("legacy-session");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "platform-user-1", "platform-session-1");

        assertThatThrownBy(() -> controller.queryMe(request, servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("Legacy request identity fields are not allowed on auth-aware runtime endpoint /api/chat/me/query")
            .hasMessageContaining("userId, sessionId");
    }

    @Test
    void meQueryRequiresVerifiedAuthContext() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        ChatRuntimeController controller = controllerFor(orchestrator, null, strictAuthResolver());

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Explain the failure");

        assertThatThrownBy(() -> controller.queryMe(request, new MockHttpServletRequest()))
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
        request.setUserId("legacy-user");

        assertThatThrownBy(() -> controller.query(request, new MockHttpServletRequest()))
            .isInstanceOf(ResponseStatusException.class)
            .extracting(ex -> ((ResponseStatusException) ex).getStatusCode())
            .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void strictConflictModeRejectsConflictingLegacyRequestIdentityWhenVerifiedHeadersExist() {
        RAGOrchestrator orchestrator = mock(RAGOrchestrator.class);
        ChatRuntimeController controller = controllerFor(orchestrator, null, strictConflictAuthResolver());

        ChatQueryRequest request = new ChatQueryRequest();
        request.setQuery("Hello");
        request.setUserId("legacy-user");
        request.setSessionId("legacy-session");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        addVerifiedAuthHeaders(servletRequest, "platform-user-1", "platform-session-1");

        assertThatThrownBy(() -> controller.query(request, servletRequest))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("400 BAD_REQUEST")
            .hasMessageContaining("Request userId conflicts with verified runtime auth context");
    }

    @Test
    void queryUsesPublicAnonymousBearerTokenWithoutRequestIdentity() {
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
        properties.getIngress().setLegacyRequestIdentityEnabled(false);
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
        assertThat(response.getUserId()).isNull();
        assertThat(response.getSessionId()).isEqualTo("anon-public-session");
        assertThat(response.getAuthContext()).isNotNull();
        assertThat(response.getAuthContext().getSubjectId()).isEqualTo("anon-public-session");
        assertThat(response.getAuthContext().getSubjectType()).isEqualTo(RuntimeAuthSubjectType.ANONYMOUS_SESSION.name());
        assertThat(response.getAuthContext().getAuthMode()).isEqualTo(RuntimeAuthMode.PUBLIC_RUNTIME_ANONYMOUS.name());
        assertThat(response.getAuthContext().getSessionId()).isEqualTo("anon-public-session");
        assertThat(responseEntity.getHeaders().getFirst("X-AIFABRIC-RUNTIME-AUTH-MODE"))
            .isEqualTo(RuntimeAuthMode.PUBLIC_RUNTIME_ANONYMOUS.name());
        assertThat(responseEntity.getHeaders().getFirst("X-AIFABRIC-RUNTIME-COMPATIBILITY-IDENTITY"))
            .isEqualTo("false");

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
    void queryUsesPublicAuthenticatedBearerTokenWithoutLegacyRequestIdentity() {
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
        assertThat(response.getUserId()).isEqualTo("customer-123");
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
        ChatRuntimeController controller = instantiateController(
            provider(orchestrator),
            mock(RuntimeConversationGateway.class),
            provider(null),
            provider(null),
            provider(promptConfigService),
            authResolver
        );
        ReflectionTestUtils.setField(controller, "adminApiKey", "preview-secret");
        ReflectionTestUtils.setField(controller, "adminApiKeyHeader", "X-ADMIN-API-KEY");
        return controller;
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
        return new RuntimeRequestAuthResolver(new RuntimeAuthProperties());
    }

    private RuntimeRequestAuthResolver strictAuthResolver() {
        RuntimeAuthProperties properties = new RuntimeAuthProperties();
        properties.getIngress().setMode(RuntimeAuthIngressMode.VERIFIED_CONTEXT_REQUIRED);
        properties.getIngress().setLegacyRequestIdentityEnabled(false);
        properties.getIngress().getTrustedBackend().setApiKeyValue("runtime-secret");
        return new RuntimeRequestAuthResolver(properties);
    }

    private RuntimeRequestAuthResolver strictConflictAuthResolver() {
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
        request.addHeader("X-AIFABRIC-AUTH-SUBJECT-TYPE", RuntimeAuthSubjectType.INTERNAL_PLATFORM_USER.name());
        request.addHeader("X-AIFABRIC-AUTH-MODE", RuntimeAuthMode.PLATFORM_PROXY_SESSION.name());
        request.addHeader("X-AIFABRIC-AUTH-CALLER-TYPE", RuntimeAuthCallerType.PLATFORM_PROXY.name());
        request.addHeader("X-AIFABRIC-AUTH-SESSION-ID", sessionId);
        request.addHeader("X-AIFABRIC-AUTH-DEPLOYMENT-ID", "dep-123");
        request.addHeader("X-AIFABRIC-AUTH-ISSUER", "platform-backend");
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ObjectProvider provider(Object value) {
        ObjectProvider provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
