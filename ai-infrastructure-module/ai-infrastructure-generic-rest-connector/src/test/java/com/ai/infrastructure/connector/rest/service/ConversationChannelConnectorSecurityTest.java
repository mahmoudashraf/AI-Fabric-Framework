package com.ai.infrastructure.connector.rest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ConversationChannelConnectorSecurityTest {

    private static final String SECRET = "connector-signing-secret-1234567890-abcdef";

    @Test
    void signsAndVerifiesTheExactTimestampNonceAndPayload() {
        String timestamp = "1791860400";
        String nonce = "nonce-1";
        String body = "{\"eventId\":\"event-1\"}";
        String signature = ConversationChannelConnectorSecurity.sign(
            SECRET,
            timestamp,
            nonce,
            body
        );

        assertThat(ConversationChannelConnectorSecurity.verify(
            SECRET,
            timestamp,
            nonce,
            body,
            signature
        )).isTrue();
        assertThat(ConversationChannelConnectorSecurity.verify(
            SECRET,
            timestamp,
            nonce,
            body + " ",
            signature
        )).isFalse();
        assertThat(ConversationChannelConnectorSecurity.verify(
            SECRET,
            timestamp,
            "nonce-2",
            body,
            signature
        )).isFalse();
    }

    @Test
    void enforcesClockSkewAndSecretStrength() {
        Instant now = Instant.ofEpochSecond(1_791_860_400L);

        assertThat(ConversationChannelConnectorSecurity.timestampWithinSkew(
            Long.toString(now.minusSeconds(300).getEpochSecond()),
            now,
            300
        )).isTrue();
        assertThat(ConversationChannelConnectorSecurity.timestampWithinSkew(
            Long.toString(now.minusSeconds(301).getEpochSecond()),
            now,
            300
        )).isFalse();
        assertThat(ConversationChannelConnectorSecurity.timestampWithinSkew(
            "not-a-timestamp",
            now,
            300
        )).isFalse();
        assertThatThrownBy(() -> ConversationChannelConnectorSecurity.sign(
            "short",
            "1",
            "nonce",
            "{}"
        )).isInstanceOf(IllegalStateException.class);
    }
}
