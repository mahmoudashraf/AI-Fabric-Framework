package com.ai.infrastructure.datasync.service;

import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.datasync.AIDataSyncProperties;
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

        DataSyncOperationResponse response = service.upsert(DataSyncUpsertRequest.builder()
            .vectorSpace("product")
            .id("p1")
            .content("hello")
            .trace(DataSyncTrace.builder().userId("system").requestId("req1").build())
            .build());

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

        DataSyncOperationResponse response = service.upsert(DataSyncUpsertRequest.builder()
            .vectorSpace("product")
            .id("p1")
            .content("hello")
            .trace(DataSyncTrace.builder().userId("system").requestId("req1").build())
            .build());

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo("ACCESS_DENIED");
    }
}

