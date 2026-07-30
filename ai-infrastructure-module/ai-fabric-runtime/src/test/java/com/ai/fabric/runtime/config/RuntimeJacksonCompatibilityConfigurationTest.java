package com.ai.fabric.runtime.config;

import ai.fabric.indexing.api.AIIndexWorkType;
import ai.fabric.indexing.api.AIProcessOperation;
import ai.fabric.indexing.model.AIIndexDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeJacksonCompatibilityConfigurationTest {

    @Test
    void compatibilityMapperRoundTripsDurableIndexDocuments() throws Exception {
        ObjectMapper objectMapper = new RuntimeJacksonCompatibilityConfiguration()
            .jackson2ObjectMapper();
        AIIndexDocument original = new AIIndexDocument(
            AIIndexDocument.CURRENT_SCHEMA_VERSION,
            "a".repeat(64),
            "document",
            "doc-1",
            AIIndexWorkType.UPSERT,
            AIProcessOperation.UPDATE,
            "Searchable evidence",
            "Grounded context",
            Map.of("tenantId", "ten-1"),
            Map.of(),
            Map.of(),
            1L,
            "request-1",
            Instant.parse("2026-07-30T12:00:00Z")
        );

        AIIndexDocument restored = objectMapper.readValue(
            objectMapper.writeValueAsBytes(original),
            AIIndexDocument.class
        );

        assertThat(restored).isEqualTo(original);
    }
}
