package com.ai.fabric.runtime.web.admin;

import ai.fabric.config.AIEntityConfigurationLoader;
import ai.fabric.entity.IndexingQueueEntry;
import ai.fabric.indexing.IndexingStatus;
import ai.fabric.indexing.api.AIIndexWorkType;
import ai.fabric.indexing.api.AIProcessOperation;
import ai.fabric.indexing.api.IndexingStrategy;
import ai.fabric.indexing.queue.IndexingQueueService;
import ai.fabric.rag.VectorDatabaseService;
import ai.fabric.repository.IndexingQueueRepository;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VectorIndexAdminControllerTest {

    @Test
    void workStatusReturnsBoundedQueueMetadataWithoutPayloads() {
        IndexingQueueService queueService = mock(IndexingQueueService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<IndexingQueueService> provider =
            mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(queueService);

        IndexingQueueEntry entry = mock(IndexingQueueEntry.class);
        when(entry.getId()).thenReturn(71L);
        when(entry.getStatus()).thenReturn(IndexingStatus.PROCESSING);
        when(entry.getEntityType()).thenReturn("product");
        when(entry.getEntityId()).thenReturn("product-1");
        when(entry.getWorkType()).thenReturn(AIIndexWorkType.UPSERT);
        when(entry.getSourceOperation()).thenReturn(AIProcessOperation.UPDATE);
        when(entry.getStrategy()).thenReturn(IndexingStrategy.ASYNC);
        when(entry.getRetryCount()).thenReturn(1);
        when(entry.getMaxRetries()).thenReturn(3);
        when(entry.getCorrelationId()).thenReturn("sync-1");
        when(entry.getUpdatedAt()).thenReturn(
            LocalDateTime.parse("2026-07-30T12:30:00")
        );
        when(queueService.requireEntry(71L)).thenReturn(entry);

        VectorIndexAdminController controller = controller(provider);
        ResponseEntity<?> response = controller.workStatus(
            "71",
            new MockHttpServletRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body)
            .containsEntry("workId", "71")
            .containsEntry("status", "PROCESSING")
            .containsEntry("entityType", "product")
            .containsEntry("entityId", "product-1")
            .containsEntry("terminal", false)
            .containsEntry("successfulTerminal", false);
        assertThat(body)
            .doesNotContainKeys(
                "payload",
                "resultPayload",
                "descriptorHash",
                "processingNode"
            );
    }

    @Test
    void workStatusFailsClosedWhenIndexingCapabilityIsUnavailable() {
        @SuppressWarnings("unchecked")
        ObjectProvider<IndexingQueueService> provider =
            mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        ResponseEntity<?> response = controller(provider).workStatus(
            "71",
            new MockHttpServletRequest()
        );

        assertThat(response.getStatusCode())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body)
            .containsEntry("errorCode", "INDEXING_WORK_STATUS_UNAVAILABLE");
    }

    private VectorIndexAdminController controller(
        ObjectProvider<IndexingQueueService> provider
    ) {
        return new VectorIndexAdminController(
            mock(VectorDatabaseService.class),
            mock(AIEntityConfigurationLoader.class),
            mock(RuntimeRequestAuthResolver.class),
            provider,
            emptyQueueRepositoryProvider()
        );
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<IndexingQueueRepository>
        emptyQueueRepositoryProvider() {
        ObjectProvider<IndexingQueueRepository> provider =
            mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}
