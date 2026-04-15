package com.ai.infrastructure.vector.qdrant;

import com.ai.infrastructure.config.AIProviderConfig;
import com.ai.infrastructure.dto.AISearchRequest;
import com.google.common.util.concurrent.Futures;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

    @Test
    void adminDiagnosticsExposeResolvedCollectionScope() {
        AIProviderConfig config = new AIProviderConfig();
        AIProviderConfig.QdrantConfig qdrant = config.getQdrant();
        qdrant.setEnabled(true);
        qdrant.setHost("qdrant.internal");
        qdrant.setCollectionPrefix("customer_a__tenant_b__");

        QdrantVectorDatabaseService service = new QdrantVectorDatabaseService(config);

        assertThat(service.adminDiagnostics())
            .containsEntry("sharedStorage", true)
            .containsEntry("scopeType", "COLLECTION_PREFIX")
            .containsEntry("rootResourceValue", "qdrant.internal")
            .containsEntry("scopePrefix", "customer_a__tenant_b__")
            .containsEntry("scopePattern", "customer_a__tenant_b__<entity_type>");
    }

    @Test
    void searchEnsuresKnowledgeSourceHandlePayloadIndexForExistingCollection() {
        AIProviderConfig config = baseConfig();
        QdrantClient client = mock(QdrantClient.class);
        when(client.listCollectionsAsync()).thenReturn(Futures.immediateFuture(List.of("customer_a__tenant_b__faq-article")));
        when(client.getCollectionInfoAsync("customer_a__tenant_b__faq-article"))
            .thenReturn(Futures.immediateFuture(Collections.CollectionInfo.newBuilder().build()));
        when(client.createPayloadIndexAsync(
            eq("customer_a__tenant_b__faq-article"),
            eq("knowledgeSourceHandleRef"),
            eq(Collections.PayloadSchemaType.Keyword),
            isNull(),
            eq(true),
            isNull(),
            isNull()
        )).thenReturn(Futures.immediateFuture(Points.UpdateResult.getDefaultInstance()));
        when(client.searchAsync(any())).thenReturn(Futures.immediateFuture(List.of()));

        QdrantVectorDatabaseService service = new QdrantVectorDatabaseService(config, null, client);

        service.search(List.of(0.1d, 0.2d), AISearchRequest.builder()
            .query("reset password")
            .entityType("faq-article")
            .limit(5)
            .build());

        verify(client).createPayloadIndexAsync(
            eq("customer_a__tenant_b__faq-article"),
            eq("knowledgeSourceHandleRef"),
            eq(Collections.PayloadSchemaType.Keyword),
            isNull(),
            eq(true),
            isNull(),
            isNull()
        );
    }

    @Test
    void searchSkipsKnowledgeSourceHandlePayloadIndexCreationWhenSchemaAlreadyExists() {
        AIProviderConfig config = baseConfig();
        QdrantClient client = mock(QdrantClient.class);
        when(client.listCollectionsAsync()).thenReturn(Futures.immediateFuture(List.of("customer_a__tenant_b__faq-article")));
        when(client.getCollectionInfoAsync("customer_a__tenant_b__faq-article"))
            .thenReturn(Futures.immediateFuture(
                Collections.CollectionInfo.newBuilder()
                    .putPayloadSchema("knowledgeSourceHandleRef", Collections.PayloadSchemaInfo.getDefaultInstance())
                    .build()
            ));
        when(client.searchAsync(any())).thenReturn(Futures.immediateFuture(List.of()));

        QdrantVectorDatabaseService service = new QdrantVectorDatabaseService(config, null, client);

        service.search(List.of(0.1d, 0.2d), AISearchRequest.builder()
            .query("reset password")
            .entityType("faq-article")
            .limit(5)
            .build());

        verify(client, never()).createPayloadIndexAsync(
            eq("customer_a__tenant_b__faq-article"),
            eq("knowledgeSourceHandleRef"),
            eq(Collections.PayloadSchemaType.Keyword),
            isNull(),
            eq(true),
            isNull(),
            isNull()
        );
    }

    @Test
    void searchFallsBackToUnfilteredQueryWhenPayloadIndexIsStillMissing() {
        AIProviderConfig config = baseConfig();
        QdrantClient client = mock(QdrantClient.class);
        when(client.listCollectionsAsync()).thenReturn(Futures.immediateFuture(List.of("customer_a__tenant_b__faq-article")));
        when(client.getCollectionInfoAsync("customer_a__tenant_b__faq-article"))
            .thenReturn(Futures.immediateFuture(Collections.CollectionInfo.newBuilder().build()));
        when(client.createPayloadIndexAsync(
            eq("customer_a__tenant_b__faq-article"),
            eq("knowledgeSourceHandleRef"),
            eq(Collections.PayloadSchemaType.Keyword),
            isNull(),
            eq(true),
            isNull(),
            isNull()
        )).thenReturn(Futures.immediateFuture(Points.UpdateResult.getDefaultInstance()));
        when(client.searchAsync(any()))
            .thenReturn(
                Futures.immediateFailedFuture(new RuntimeException(
                    "INVALID_ARGUMENT: Bad request: Index required but not found for \"knowledgeSourceHandleRef\""
                )),
                Futures.immediateFuture(List.of())
            );

        QdrantVectorDatabaseService service = new QdrantVectorDatabaseService(config, null, client);

        service.search(List.of(0.1d, 0.2d), AISearchRequest.builder()
            .query("reset password")
            .entityType("faq-article")
            .limit(5)
            .metadata(java.util.Map.of("knowledgeSourceHandleRef", "plugin/mkp-data-help-center"))
            .build());

        verify(client, atLeast(2)).searchAsync(any());
    }

    private AIProviderConfig baseConfig() {
        AIProviderConfig config = new AIProviderConfig();
        AIProviderConfig.QdrantConfig qdrant = config.getQdrant();
        qdrant.setEnabled(true);
        qdrant.setHost("qdrant.internal");
        qdrant.setCollectionPrefix("customer_a__tenant_b__");
        return config;
    }
}
