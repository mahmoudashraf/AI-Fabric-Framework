package com.ai.fabric.vectorization.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StableVectorizationIdentityTest {

    @Test
    void effectiveTargetIdUsesDeterministicChunkSuffix() {
        StableVectorizationIdentity identity = new StableVectorizationIdentity("product-1", "source-1", "Chunk 0001");

        assertThat(identity.effectiveTargetId()).isEqualTo("product-1::chunk:chunk-0001");
    }
}
