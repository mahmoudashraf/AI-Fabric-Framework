package com.ai.infrastructure.intent.orchestration;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class OrchestrationContextTest {

    @Test
    void authenticatedContext_shouldHaveUserId() {
        OrchestrationContext context = OrchestrationContext.forUser("user-123");

        assertThat(context.isAuthenticated()).isTrue();
        assertThat(context.isAnonymous()).isFalse();
        assertThat(context.getUserId()).isEqualTo("user-123");
        assertThat(context.getIdentifier()).isEqualTo("user-123");
    }

    @Test
    void anonymousContext_shouldHaveSessionId() {
        OrchestrationContext context = OrchestrationContext.forSession("sess-456");

        assertThat(context.isAuthenticated()).isFalse();
        assertThat(context.isAnonymous()).isTrue();
        assertThat(context.getSessionId()).isEqualTo("sess-456");
        assertThat(context.getIdentifier()).isEqualTo("sess-456");
    }

    @Test
    void validate_withUserId_shouldPass() {
        OrchestrationContext context = OrchestrationContext.forUser("user-123");

        assertThatCode(context::validate).doesNotThrowAnyException();
    }

    @Test
    void validate_withSessionId_shouldPass() {
        OrchestrationContext context = OrchestrationContext.forSession("sess-456");

        assertThatCode(context::validate).doesNotThrowAnyException();
    }

    @Test
    void validate_withoutIdentifier_shouldFail() {
        OrchestrationContext context = OrchestrationContext.builder().build();

        assertThatThrownBy(context::validate)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("userId")
            .hasMessageContaining("sessionId");
    }

    @Test
    void getOrGenerateRequestId_shouldGenerateIfMissing() {
        OrchestrationContext context = OrchestrationContext.forUser("user-123");

        String requestId = context.getOrGenerateRequestId();

        assertThat(requestId).isNotNull().startsWith("rag-");
        assertThat(context.getRequestId()).isEqualTo(requestId);
    }

    @Test
    void getOrGenerateRequestId_shouldReturnExisting() {
        OrchestrationContext context = OrchestrationContext.builder()
            .userId("user-123")
            .requestId("custom-123")
            .build();

        assertThat(context.getOrGenerateRequestId()).isEqualTo("custom-123");
    }

    @Test
    void builder_shouldSupportFullContext() {
        OrchestrationContext context = OrchestrationContext.builder()
            .userId("user-123")
            .sessionId("sess-456")
            .requestId("req-789")
            .ipAddress("192.168.1.1")
            .userAgent("Mozilla/5.0")
            .locale(Locale.US)
            .metadata(Map.of("tier", "premium"))
            .build();

        assertThat(context.getUserId()).isEqualTo("user-123");
        assertThat(context.getSessionId()).isEqualTo("sess-456");
        assertThat(context.getRequestId()).isEqualTo("req-789");
        assertThat(context.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(context.getUserAgent()).isEqualTo("Mozilla/5.0");
        assertThat(context.getLocale()).isEqualTo(Locale.US);
        assertThat(context.getMetadata()).containsEntry("tier", "premium");
    }

    @Test
    void anonymous_shouldGenerateSessionId() {
        OrchestrationContext context = OrchestrationContext.anonymous();

        assertThat(context.isAnonymous()).isTrue();
        assertThat(context.getSessionId()).isNotNull().startsWith("anon-");
    }
}
