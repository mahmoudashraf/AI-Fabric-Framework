package com.ai.fabric.runtime;

import com.ai.fabric.runtime.web.ChatRuntimeController;
import com.ai.fabric.runtime.web.dto.ChatQueryRequest;
import com.ai.fabric.runtime.web.dto.ChatQueryResponse;
import com.ai.infrastructure.chat.service.ChatSessionService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.RAGOrchestrator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatusCode;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    private ChatRuntimeController controllerFor(RAGOrchestrator orchestrator) {
        ChatRuntimeController controller = new ChatRuntimeController(
            provider(orchestrator),
            provider(null),
            provider(null),
            provider(null)
        );
        ReflectionTestUtils.setField(controller, "adminApiKey", "preview-secret");
        ReflectionTestUtils.setField(controller, "adminApiKeyHeader", "X-ADMIN-API-KEY");
        return controller;
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
