package com.ai.infrastructure.vector.qdrant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QdrantVectorDatabaseServiceTest {

    @Test
    void scopedCollectionNamePrependsConfiguredPrefix() {
        assertThat(QdrantVectorDatabaseService.scopedCollectionName("product", "customer_a__tenant_b__"))
            .isEqualTo("customer_a__tenant_b__product");
    }

    @Test
    void scopedCollectionNameLeavesEntityTypeUntouchedWithoutPrefix() {
        assertThat(QdrantVectorDatabaseService.scopedCollectionName("product", ""))
            .isEqualTo("product");
    }
}
