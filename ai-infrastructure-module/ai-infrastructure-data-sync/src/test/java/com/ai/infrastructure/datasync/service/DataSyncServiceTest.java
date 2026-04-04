package com.ai.infrastructure.datasync.service;

import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.datasync.AIDataSyncProperties;
import com.ai.infrastructure.datasync.dto.DataSyncIdentity;
import com.ai.infrastructure.datasync.dto.DataSyncOperationResponse;
import com.ai.infrastructure.datasync.dto.DataSyncTrace;
import com.ai.infrastructure.datasync.dto.DataSyncUpsertRequest;
import com.ai.infrastructure.datasync.normalize.DataSyncEntityNormalizer;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.service.VectorManagementService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataSyncServiceTest {

    @Test
    void upsert_shouldStoreVector_whenAccessGranted() {
        AIDataSyncProperties props = new AIDataSyncProperties();
        AIEntityConfigurationLoader loader = mock(AIEntityConfigurationLoader.class);
        AIEmbeddingService embeddingService = mock(AIEmbeddingService.class);
        VectorManagementService vectorManagementService = mock(VectorManagementService.class);
        AIAccessControlService accessControlService = mock(AIAccessControlService.class);

        AIEntityConfig config = AIEntityConfig.builder()
            .entityType("product")
            .indexable(true)
            .build();
        when(loader.getEntityConfig("product")).thenReturn(config);

        when(accessControlService.checkAccess(any())).thenReturn(AIAccessControlResponse.builder()
            .accessGranted(true)
            .build());

        when(embeddingService.generateEmbedding(any())).thenReturn(AIEmbeddingResponse.builder()
            .embedding(List.of(0.1, 0.2))
            .build());

        when(vectorManagementService.storeVector(anyString(), anyString(), anyString(), any(), any()))
            .thenReturn("vec_1");

        DataSyncEntityNormalizer normalizer = new DataSyncEntityNormalizer(props, null);
        Clock clock = Clock.fixed(Instant.parse("2026-02-12T00:00:00Z"), ZoneOffset.UTC);

        DataSyncService service = new DataSyncService(
            props,
            loader,
            embeddingService,
            vectorManagementService,
            accessControlService,
            normalizer,
            clock
        );

        DataSyncTrace trace = new DataSyncTrace();
        trace.setUserId("system");
        trace.setRequestId("req1");

        DataSyncUpsertRequest request = new DataSyncUpsertRequest();
        request.setVectorSpace("product");
        request.setId("p1");
        request.setContent("hello");
        request.setTrace(trace);

        DataSyncOperationResponse response = service.upsert(request);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getVectorId()).isEqualTo("vec_1");
        verify(vectorManagementService).storeVector(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void upsert_shouldFailClosed_whenAccessDenied() {
        AIDataSyncProperties props = new AIDataSyncProperties();
        AIEntityConfigurationLoader loader = mock(AIEntityConfigurationLoader.class);
        AIEmbeddingService embeddingService = mock(AIEmbeddingService.class);
        VectorManagementService vectorManagementService = mock(VectorManagementService.class);
        AIAccessControlService accessControlService = mock(AIAccessControlService.class);

        when(loader.getEntityConfig("product")).thenReturn(AIEntityConfig.builder()
            .entityType("product")
            .indexable(true)
            .build());

        when(accessControlService.checkAccess(any())).thenReturn(AIAccessControlResponse.builder()
            .accessGranted(false)
            .build());

        DataSyncEntityNormalizer normalizer = new DataSyncEntityNormalizer(props, null);
        Clock clock = Clock.fixed(Instant.parse("2026-02-12T00:00:00Z"), ZoneOffset.UTC);

        DataSyncService service = new DataSyncService(
            props,
            loader,
            embeddingService,
            vectorManagementService,
            accessControlService,
            normalizer,
            clock
        );

        DataSyncTrace trace = new DataSyncTrace();
        trace.setUserId("system");
        trace.setRequestId("req1");

        DataSyncUpsertRequest request = new DataSyncUpsertRequest();
        request.setVectorSpace("product");
        request.setId("p1");
        request.setContent("hello");
        request.setTrace(trace);

        DataSyncOperationResponse response = service.upsert(request);

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("ACCESS_DENIED");
    }

    @Test
    void upsert_shouldUseDeterministicChunkIdentityAndMetadata() {
        AIDataSyncProperties props = new AIDataSyncProperties();
        AIEntityConfigurationLoader loader = mock(AIEntityConfigurationLoader.class);
        AIEmbeddingService embeddingService = mock(AIEmbeddingService.class);
        VectorManagementService vectorManagementService = mock(VectorManagementService.class);
        AIAccessControlService accessControlService = mock(AIAccessControlService.class);

        when(loader.getEntityConfig("product")).thenReturn(AIEntityConfig.builder()
            .entityType("product")
            .indexable(true)
            .build());
        when(accessControlService.checkAccess(any())).thenReturn(AIAccessControlResponse.builder()
            .accessGranted(true)
            .build());
        when(embeddingService.generateEmbedding(any())).thenReturn(AIEmbeddingResponse.builder()
            .embedding(List.of(0.1, 0.2))
            .build());
        when(vectorManagementService.storeVector(eq("product"), eq("p1::chunk:segment-0001"), anyString(), any(), any()))
            .thenReturn("vec_2");

        DataSyncEntityNormalizer normalizer = new DataSyncEntityNormalizer(props, null);
        DataSyncService service = new DataSyncService(
            props,
            loader,
            embeddingService,
            vectorManagementService,
            accessControlService,
            normalizer,
            Clock.fixed(Instant.parse("2026-02-12T00:00:00Z"), ZoneOffset.UTC)
        );

        DataSyncTrace trace = new DataSyncTrace();
        trace.setUserId("vectorization-runner");
        trace.setRequestId("req2");

        DataSyncIdentity identity = new DataSyncIdentity();
        identity.setSourceRecordId("source-product-1");
        identity.setSourceRecordVersion("42");
        identity.setChunkId("Segment 0001");
        identity.setChunkCount(3);
        identity.setContentFingerprint("sha256:abc");

        DataSyncUpsertRequest request = new DataSyncUpsertRequest();
        request.setVectorSpace("product");
        request.setId("p1");
        request.setContent("hello");
        request.setIdentity(identity);
        request.setTrace(trace);

        DataSyncOperationResponse response = service.upsert(request);

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getId()).isEqualTo("p1::chunk:segment-0001");
        assertThat(response.getMetadata())
            .containsEntry("_dataSyncSourceRecordId", "source-product-1")
            .containsEntry("_dataSyncSourceRecordVersion", "42")
            .containsEntry("_dataSyncChunkId", "segment-0001")
            .containsEntry("_dataSyncChunkCount", 3)
            .containsEntry("_dataSyncContentFingerprint", "sha256:abc")
            .containsEntry("_dataSyncTargetId", "p1::chunk:segment-0001");
    }
}
