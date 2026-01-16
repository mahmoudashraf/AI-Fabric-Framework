package com.ai.infrastructure.security;

import com.ai.infrastructure.config.SecurityProperties;
import com.ai.infrastructure.dto.AISecurityRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AISecurityServiceTest {

    @Test
    void shouldAllowAnonymousRequestsWhenSessionIdPresent() {
        SecurityProperties properties = new SecurityProperties();
        properties.setBlockOnPiiDetection(false);

        AISecurityService service = new AISecurityService(
            null,
            Clock.fixed(Instant.parse("2026-01-16T00:00:00Z"), ZoneOffset.UTC),
            properties
        );

        AISecurityRequest request = AISecurityRequest.builder()
            .requestId("req-1")
            .sessionId("session-123")
            .content("hello")
            .operationType("INTENT_QUERY")
            .build();

        var response = service.analyzeRequest(request);

        assertThat(response).isNotNull();
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getShouldBlock()).isFalse();
        assertThat(response.getUserId()).isEqualTo("session-123");
    }
}
