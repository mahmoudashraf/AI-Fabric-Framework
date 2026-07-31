package com.ai.fabric.runtime.web.admin;

import ai.fabric.config.AIEntityConfigurationLoader;
import ai.fabric.indexing.api.AIIndexWorkType;
import ai.fabric.indexing.api.AIProcessOperation;
import ai.fabric.indexing.api.IndexingStrategy;
import ai.fabric.indexing.api.IndexingWorkQuery;
import ai.fabric.indexing.api.IndexingWorkState;
import ai.fabric.indexing.api.IndexingWorkStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VectorIndexAdminControllerTest {

    @Test
    void workStatusReturnsBoundedQueueMetadataWithoutPayloads() {
        IndexingWorkQuery workQuery = mock(IndexingWorkQuery.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<IndexingWorkQuery> provider =
            mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(workQuery);

        IndexingWorkStatus work = new IndexingWorkStatus(
            "71",
            "product",
            "product-1",
            AIIndexWorkType.UPSERT,
            AIProcessOperation.UPDATE,
            IndexingStrategy.ASYNC,
            IndexingWorkState.PROCESSING,
            1,
            3,
            null,
            null,
            "sync-1",
            null,
            null,
            null,
            null,
            null,
            LocalDateTime.parse("2026-07-30T12:30:00")
        );
        when(workQuery.findByWorkId("71")).thenReturn(Optional.of(work));

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
        ObjectProvider<IndexingWorkQuery> provider =
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

    @Test
    void workStatusReturnsNotFoundForUnknownOpaqueWorkId() {
        IndexingWorkQuery workQuery = mock(IndexingWorkQuery.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<IndexingWorkQuery> provider =
            mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(workQuery);
        when(workQuery.findByWorkId("999")).thenReturn(Optional.empty());

        ResponseEntity<?> response = controller(provider).workStatus(
            "999",
            new MockHttpServletRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body)
            .containsEntry("errorCode", "INDEXING_WORK_NOT_FOUND");
    }

    @Test
    void workStatusReturnsBadRequestWhenFrameworkRejectsWorkId() {
        IndexingWorkQuery workQuery = mock(IndexingWorkQuery.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<IndexingWorkQuery> provider =
            mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(workQuery);
        when(workQuery.findByWorkId("bad"))
            .thenThrow(new IllegalArgumentException("invalid"));

        ResponseEntity<?> response = controller(provider).workStatus(
            "bad",
            new MockHttpServletRequest()
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> body = (Map<String, Object>) response.getBody();
        assertThat(body)
            .containsEntry("errorCode", "INVALID_INDEXING_WORK_ID");
    }

    private VectorIndexAdminController controller(
        ObjectProvider<IndexingWorkQuery> provider
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
