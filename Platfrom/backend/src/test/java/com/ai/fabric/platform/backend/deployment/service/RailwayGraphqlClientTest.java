package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RailwayGraphqlClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void hasMeaningfulStagedChangesReturnsFalseForEmptyPatchPlaceholder() throws Exception {
        assertThat(RailwayGraphqlClient.hasMeaningfulStagedChanges(objectMapper.readTree("""
            {
              "id": "<empty>",
              "status": "STAGED",
              "message": null,
              "lastAppliedError": null
            }
            """))).isFalse();
    }

    @Test
    void hasMeaningfulStagedChangesReturnsTrueForRealPatch() throws Exception {
        assertThat(RailwayGraphqlClient.hasMeaningfulStagedChanges(objectMapper.readTree("""
            {
              "id": "patch-123",
              "status": "STAGED"
            }
            """))).isTrue();
    }
}
