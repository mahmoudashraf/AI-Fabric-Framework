package com.ai.infrastructure.relay.service;

import com.ai.infrastructure.relay.api.TraceContextDto;
import com.ai.infrastructure.relay.api.VerifiedAuthContextDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RelayTraceContextSupportTest {

    @Test
    void verifiedAuthContextIsPreferredForForwardHeadersAndRateLimiting() {
        TraceContextDto trace = new TraceContextDto(
            "req_1",
            "chat_1",
            "legacy-user",
            "legacy-session",
            new VerifiedAuthContextDto(
                "verified-user",
                "END_USER",
                "PUBLIC_RUNTIME_AUTHENTICATED",
                "PUBLIC_BROWSER",
                "verified-session",
                "dep-123",
                "cus-123",
                "ten-123",
                "shopify-app",
                "2026-04-07T01:45:00Z",
                List.of("chat:query", "chat:actions")
            )
        );

        Map<String, String> headers = RelayTraceContextSupport.forwardHeaders(trace);

        assertThat(headers)
            .containsEntry("X-AIFABRIC-REQUEST-ID", "req_1")
            .containsEntry("X-AIFABRIC-CONVERSATION-ID", "chat_1")
            .containsEntry("X-AIFABRIC-AUTH-SUBJECT-ID", "verified-user")
            .containsEntry("X-AIFABRIC-AUTH-SUBJECT-TYPE", "END_USER")
            .containsEntry("X-AIFABRIC-AUTH-MODE", "PUBLIC_RUNTIME_AUTHENTICATED")
            .containsEntry("X-AIFABRIC-AUTH-CALLER-TYPE", "PUBLIC_BROWSER")
            .containsEntry("X-AIFABRIC-AUTH-SESSION-ID", "verified-session")
            .containsEntry("X-AIFABRIC-AUTH-DEPLOYMENT-ID", "dep-123")
            .containsEntry("X-AIFABRIC-AUTH-CUSTOMER-ID", "cus-123")
            .containsEntry("X-AIFABRIC-AUTH-TENANT-ID", "ten-123")
            .containsEntry("X-AIFABRIC-AUTH-ISSUER", "shopify-app")
            .containsEntry("X-AIFABRIC-AUTH-EXPIRES-AT", "2026-04-07T01:45:00Z")
            .containsEntry("X-AIFABRIC-AUTH-SCOPES", "chat:query,chat:actions");
        assertThat(headers)
            .doesNotContainKeys("X-AIFABRIC-USER-ID", "X-AIFABRIC-SESSION-ID");

        assertThat(RelayTraceContextSupport.rateLimitKey(trace)).isEqualTo("verified-user");
        assertThat(RelayTraceContextSupport.effectiveSubjectId(trace)).isEqualTo("verified-user");
        assertThat(RelayTraceContextSupport.effectiveSessionId(trace)).isEqualTo("verified-session");
        assertThat(RelayTraceContextSupport.subjectType(trace)).isEqualTo("END_USER");
        assertThat(RelayTraceContextSupport.authMode(trace)).isEqualTo("PUBLIC_RUNTIME_AUTHENTICATED");
        assertThat(RelayTraceContextSupport.authIssuer(trace)).isEqualTo("shopify-app");
        assertThat(RelayTraceContextSupport.grantedScopes(trace)).containsExactly("chat:query", "chat:actions");
    }

    @Test
    void legacyTraceFieldsRemainAsFallbackWhenVerifiedAuthContextIsAbsent() {
        TraceContextDto trace = new TraceContextDto("req_2", "chat_2", "legacy-user", "legacy-session", null);

        Map<String, String> headers = RelayTraceContextSupport.forwardHeaders(trace);

        assertThat(headers)
            .containsEntry("X-AIFABRIC-REQUEST-ID", "req_2")
            .containsEntry("X-AIFABRIC-CONVERSATION-ID", "chat_2")
            .containsEntry("X-AIFABRIC-USER-ID", "legacy-user")
            .containsEntry("X-AIFABRIC-SESSION-ID", "legacy-session");

        assertThat(RelayTraceContextSupport.rateLimitKey(trace)).isEqualTo("legacy-user");
        assertThat(RelayTraceContextSupport.effectiveSubjectId(trace)).isEqualTo("legacy-user");
        assertThat(RelayTraceContextSupport.effectiveSessionId(trace)).isEqualTo("legacy-session");
        assertThat(RelayTraceContextSupport.grantedScopes(trace)).isEmpty();
    }
}
