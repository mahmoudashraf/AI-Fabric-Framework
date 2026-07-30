package com.ai.fabric.vectorization.runner.service;

import com.ai.fabric.integration.connection.ConnectionDescriptor;
import com.ai.fabric.integration.credential.ResolvedSourceAuthMaterial;
import com.ai.fabric.vectorization.adapter.source.VectorizationSourceAdapter;
import com.ai.fabric.vectorization.mapping.VectorizationRecordMapper;
import com.ai.fabric.vectorization.model.TargetConnectionDescriptor;
import com.ai.fabric.vectorization.model.VectorizationDiscoveryResult;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationMappedRecord;
import com.ai.fabric.vectorization.model.VectorizationRunReason;
import com.ai.fabric.vectorization.model.VectorizationRunnerMode;
import com.ai.fabric.vectorization.model.VectorizationSourcePage;
import com.ai.fabric.vectorization.model.VectorizationTargetWriteResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorizationRunExecutorTest {

    @Test
    void discoveryRunCompletesWithoutWritingTargetRecords() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        VectorizationRunnerPlatformClient platformClient = mock(VectorizationRunnerPlatformClient.class);
        VectorizationSourceAdapterRegistry sourceAdapterRegistry = mock(VectorizationSourceAdapterRegistry.class);
        VectorizationRecordMapper recordMapper = mock(VectorizationRecordMapper.class);
        ConnectorDataSyncTargetWriter targetWriter = mock(ConnectorDataSyncTargetWriter.class);
        VectorizationSourceAdapter sourceAdapter = mock(VectorizationSourceAdapter.class);

        VectorizationRunExecutor executor = new VectorizationRunExecutor(
            objectMapper,
            platformClient,
            sourceAdapterRegistry,
            recordMapper,
            targetWriter
        );

        ObjectNode empty = objectMapper.createObjectNode();
        VectorizationExecutionBundle bundle = new VectorizationExecutionBundle(
            "dep-1",
            "run-1",
            VectorizationRunReason.DISCOVERY,
            "rev-1",
            VectorizationRunnerMode.PLATFORM_MANAGED_AUTO,
            "vcn-1",
            List.of("product"),
            empty,
            empty,
            new ConnectionDescriptor("vcn-1", "Primary", "REST_API", "API_KEY", empty, empty),
            new ResolvedSourceAuthMaterial(Map.of("apiKey", "secret"), Map.of()),
            objectMapper.createObjectNode(),
            new TargetConnectionDescriptor("CONNECTOR_PROXY", "https://connector.example", "/api/ai/data-sync/batch", "/api/ai/data-sync/vector-spaces", "X-API-Key", "target")
        );
        VectorizationDiscoveryResult discovery = new VectorizationDiscoveryResult(Map.of("product", 24L), Map.of("product", com.ai.fabric.integration.discovery.DiscoveryCountMethod.EXACT));

        when(platformClient.fetchExecutionBundle("session-1", "run-1")).thenReturn(bundle);
        when(sourceAdapterRegistry.resolve("REST_API")).thenReturn(sourceAdapter);
        when(sourceAdapter.discover(bundle, List.of("product"))).thenReturn(discovery);

        executor.execute("session-1", new VectorizationRunnerPlatformClient.ClaimedRun("run-1", "DISCOVERY", "RUNNING", "RUNNING"));

        verify(platformClient).reportDiscovery("session-1", "vcn-1", discovery);
        verify(platformClient).completeRun(eq("session-1"), eq("run-1"), eq("COMPLETED"), any(), any(), any());
        verify(targetWriter, never()).upsertBatch(any(), any(), any());
        verify(sourceAdapter, never()).fetchPage(any(), any(), any(), anyInt());
    }

    @Test
    void sampleExecutionHonorsPageAndRecordLimits() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        VectorizationRunnerPlatformClient platformClient = mock(VectorizationRunnerPlatformClient.class);
        VectorizationSourceAdapterRegistry sourceAdapterRegistry = mock(VectorizationSourceAdapterRegistry.class);
        VectorizationRecordMapper recordMapper = mock(VectorizationRecordMapper.class);
        ConnectorDataSyncTargetWriter targetWriter = mock(ConnectorDataSyncTargetWriter.class);
        VectorizationSourceAdapter sourceAdapter = mock(VectorizationSourceAdapter.class);

        VectorizationRunExecutor executor = new VectorizationRunExecutor(
            objectMapper,
            platformClient,
            sourceAdapterRegistry,
            recordMapper,
            targetWriter
        );

        ObjectNode mappingConfig = objectMapper.createObjectNode();
        ObjectNode executionConfig = objectMapper.createObjectNode();
        executionConfig.put("batchSize", 100);
        executionConfig.put("maxPagesPerEntity", 1);
        executionConfig.put("maxRecordsPerEntity", 1);
        VectorizationExecutionBundle bundle = new VectorizationExecutionBundle(
            "dep-1",
            "run-1",
            VectorizationRunReason.BOOTSTRAP,
            "rev-1",
            VectorizationRunnerMode.PLATFORM_MANAGED_AUTO,
            "vcn-1",
            List.of("product"),
            mappingConfig,
            executionConfig,
            new ConnectionDescriptor("vcn-1", "Primary", "REST_API", "API_KEY", objectMapper.createObjectNode(), objectMapper.createObjectNode()),
            new ResolvedSourceAuthMaterial(Map.of("apiKey", "secret"), Map.of()),
            objectMapper.createObjectNode(),
            new TargetConnectionDescriptor("CONNECTOR_PROXY", "https://connector.example", "/api/ai/data-sync/batch", "/api/ai/data-sync/vector-spaces", "X-API-Key", "target")
        );

        ObjectNode first = objectMapper.createObjectNode();
        first.put("id", "1");
        ObjectNode second = objectMapper.createObjectNode();
        second.put("id", "2");
        when(platformClient.fetchExecutionBundle("session-1", "run-1")).thenReturn(bundle);
        when(sourceAdapterRegistry.resolve("REST_API")).thenReturn(sourceAdapter);
        when(sourceAdapter.discover(bundle, List.of("product"))).thenReturn(
            new VectorizationDiscoveryResult(Map.of("product", 2L), Map.of("product", com.ai.fabric.integration.discovery.DiscoveryCountMethod.EXACT))
        );
        when(sourceAdapter.fetchPage(bundle, "product", null, 1)).thenReturn(new VectorizationSourcePage(List.of(first, second), null, true));
        when(recordMapper.map("product", mappingConfig, first)).thenReturn(
            new VectorizationMappedRecord("product", "1", "1", null, Map.of("name", "One"), Map.of())
        );
        when(targetWriter.upsertBatch(eq(bundle), eq("product"), argThat(records -> records.size() == 1 && "1".equals(records.get(0).logicalEntityId()))))
            .thenReturn(new VectorizationTargetWriteResult(1, 0));
        when(platformClient.heartbeat("session-1", "run-1")).thenReturn(new VectorizationRunnerPlatformClient.HeartbeatDecision("RUNNING"));

        executor.execute("session-1", new VectorizationRunnerPlatformClient.ClaimedRun("run-1", "BOOTSTRAP", "RUNNING", "RUNNING"));

        verify(sourceAdapter).fetchPage(bundle, "product", null, 1);
        verify(targetWriter).upsertBatch(eq(bundle), eq("product"), argThat(records -> records.size() == 1 && "1".equals(records.get(0).logicalEntityId())));
        verify(platformClient).completeRun(eq("session-1"), eq("run-1"), eq("COMPLETED"), any(), any(), any());
    }

    @Test
    void durableDataSyncFailureIsStoredAsStructuredRunEvidenceWithoutCheckpointAdvance() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        VectorizationRunnerPlatformClient platformClient = mock(VectorizationRunnerPlatformClient.class);
        VectorizationSourceAdapterRegistry sourceAdapterRegistry = mock(VectorizationSourceAdapterRegistry.class);
        VectorizationRecordMapper recordMapper = mock(VectorizationRecordMapper.class);
        ConnectorDataSyncTargetWriter targetWriter = mock(ConnectorDataSyncTargetWriter.class);
        VectorizationSourceAdapter sourceAdapter = mock(VectorizationSourceAdapter.class);
        VectorizationRunExecutor executor = new VectorizationRunExecutor(
            objectMapper,
            platformClient,
            sourceAdapterRegistry,
            recordMapper,
            targetWriter
        );

        ObjectNode empty = objectMapper.createObjectNode();
        VectorizationExecutionBundle bundle = new VectorizationExecutionBundle(
            "dep-1",
            "run-1",
            VectorizationRunReason.BOOTSTRAP,
            "rev-1",
            VectorizationRunnerMode.PLATFORM_MANAGED_AUTO,
            "vcn-1",
            List.of("product"),
            empty,
            empty,
            new ConnectionDescriptor("vcn-1", "Primary", "REST_API", "API_KEY", empty, empty),
            new ResolvedSourceAuthMaterial(Map.of("apiKey", "secret"), Map.of()),
            objectMapper.createObjectNode(),
            new TargetConnectionDescriptor(
                "RUNTIME_DATA_SYNC",
                "https://runtime.example",
                "/api/ai/data-sync/batch",
                "/api/ai/data-sync/vector-spaces",
                "X-API-Key",
                "target"
            )
        );
        ObjectNode source = objectMapper.createObjectNode();
        source.put("id", "1");
        VectorizationMappedRecord mapped = new VectorizationMappedRecord(
            "product",
            "1",
            "1",
            "v1",
            Map.of("name", "One"),
            Map.of()
        );
        DataSyncTargetFailure failure = new DataSyncTargetFailure(
            503,
            "INDEXING_RETRYABLE",
            "Indexing was accepted for retry but is not yet complete.",
            "product",
            "1",
            "71",
            "FAILED_RETRYABLE",
            DataSyncRetryDisposition.RECONCILE_DURABLE_WORK,
            true,
            "sync-retry-1"
        );

        when(platformClient.fetchExecutionBundle("session-1", "run-1")).thenReturn(bundle);
        when(sourceAdapterRegistry.resolve("REST_API")).thenReturn(sourceAdapter);
        when(sourceAdapter.discover(bundle, List.of("product"))).thenReturn(
            new VectorizationDiscoveryResult(
                Map.of("product", 1L),
                Map.of("product", com.ai.fabric.integration.discovery.DiscoveryCountMethod.EXACT)
            )
        );
        when(sourceAdapter.fetchPage(bundle, "product", null, 100))
            .thenReturn(new VectorizationSourcePage(List.of(source), null, false));
        when(recordMapper.map("product", empty, source)).thenReturn(mapped);
        when(targetWriter.upsertBatch(eq(bundle), eq("product"), any()))
            .thenThrow(new DataSyncTargetWriteException(
                503,
                0,
                1,
                "sync-retry-1",
                List.of(failure)
            ));

        assertThatThrownBy(() -> executor.execute(
            "session-1",
            new VectorizationRunnerPlatformClient.ClaimedRun(
                "run-1",
                "BOOTSTRAP",
                "RUNNING",
                "RUNNING"
            )
        )).isInstanceOf(DataSyncTargetWriteException.class);

        verify(platformClient).completeRun(
            eq("session-1"),
            eq("run-1"),
            eq("FAILED"),
            argThat(progress -> progress.path("failedRecords").asInt() == 1),
            argThat(error -> error.path("dataSync").path("requiresWorkReconciliation").asBoolean()
                && "71".equals(error.path("dataSync").path("failures").get(0).path("indexingWorkId").asText())
                && "sync-retry-1".equals(error.path("dataSync").path("providerRequestId").asText())),
            argThat(buckets -> buckets.size() == 1
                && "INDEXING_RETRYABLE".equals(buckets.get(0).path("errorCode").asText()))
        );
        verify(platformClient, never()).reportCheckpoint(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void retryStopsBeforeSourceAccessWhileDurableWorkIsInFlight()
        throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        VectorizationRunnerPlatformClient platformClient =
            mock(VectorizationRunnerPlatformClient.class);
        VectorizationSourceAdapterRegistry sourceAdapterRegistry =
            mock(VectorizationSourceAdapterRegistry.class);
        VectorizationRecordMapper recordMapper =
            mock(VectorizationRecordMapper.class);
        ConnectorDataSyncTargetWriter targetWriter =
            mock(ConnectorDataSyncTargetWriter.class);
        VectorizationRunExecutor executor = new VectorizationRunExecutor(
            objectMapper,
            platformClient,
            sourceAdapterRegistry,
            recordMapper,
            targetWriter
        );

        ObjectNode empty = objectMapper.createObjectNode();
        ObjectNode executionConfig = objectMapper.createObjectNode();
        executionConfig.putArray("pendingDataSyncWork")
            .addObject()
            .put("workId", "71")
            .put("vectorSpace", "product")
            .put("entityId", "product-1");
        VectorizationExecutionBundle bundle = bundle(
            objectMapper,
            executionConfig
        );
        when(platformClient.fetchExecutionBundle("session-1", "run-1"))
            .thenReturn(bundle);
        when(targetWriter.readWorkStatus(bundle, "71")).thenReturn(
            new DataSyncWorkStatus(
                "71",
                "PROCESSING",
                "product",
                "product-1",
                null,
                1,
                3
            )
        );

        assertThatThrownBy(() -> executor.execute(
            "session-1",
            new VectorizationRunnerPlatformClient.ClaimedRun(
                "run-1",
                "BOOTSTRAP",
                "RUNNING",
                "RUNNING"
            )
        ))
            .isInstanceOf(DataSyncWorkReconciliationException.class)
            .hasMessageContaining("still in progress");

        verify(sourceAdapterRegistry, never()).resolve(any());
        verify(targetWriter, never()).upsertBatch(any(), any(), any());
        verify(platformClient, never()).reportCheckpoint(
            any(),
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        );
        verify(platformClient).completeRun(
            eq("session-1"),
            eq("run-1"),
            eq("FAILED"),
            any(),
            argThat(error ->
                "INDEXING_WORK_IN_PROGRESS".equals(
                    error.path("dataSync")
                        .path("reconciliation")
                        .path("errorCode")
                        .asText()
                )
                && "71".equals(
                    error.path("dataSync")
                        .path("reconciliation")
                        .path("workId")
                        .asText()
                )
            ),
            any()
        );
    }

    @Test
    void retrySkipsEntityWhoseDurableWorkAlreadyCompleted()
        throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        VectorizationRunnerPlatformClient platformClient =
            mock(VectorizationRunnerPlatformClient.class);
        VectorizationSourceAdapterRegistry sourceAdapterRegistry =
            mock(VectorizationSourceAdapterRegistry.class);
        VectorizationRecordMapper recordMapper =
            mock(VectorizationRecordMapper.class);
        ConnectorDataSyncTargetWriter targetWriter =
            mock(ConnectorDataSyncTargetWriter.class);
        VectorizationSourceAdapter sourceAdapter =
            mock(VectorizationSourceAdapter.class);
        VectorizationRunExecutor executor = new VectorizationRunExecutor(
            objectMapper,
            platformClient,
            sourceAdapterRegistry,
            recordMapper,
            targetWriter
        );

        ObjectNode mappingConfig = objectMapper.createObjectNode();
        ObjectNode executionConfig = objectMapper.createObjectNode();
        executionConfig.put("batchSize", 100);
        executionConfig.putArray("pendingDataSyncWork")
            .addObject()
            .put("workId", "71")
            .put("vectorSpace", "product")
            .put("entityId", "product-1");
        VectorizationExecutionBundle bundle = bundle(
            objectMapper,
            executionConfig
        );
        ObjectNode source = objectMapper.createObjectNode();
        source.put("id", "product-1");
        when(platformClient.fetchExecutionBundle("session-1", "run-1"))
            .thenReturn(bundle);
        when(targetWriter.readWorkStatus(bundle, "71")).thenReturn(
            new DataSyncWorkStatus(
                "71",
                "COMPLETED",
                "product",
                "product-1",
                null,
                1,
                3
            )
        );
        when(sourceAdapterRegistry.resolve("REST_API"))
            .thenReturn(sourceAdapter);
        when(sourceAdapter.discover(bundle, List.of("product"))).thenReturn(
            new VectorizationDiscoveryResult(
                Map.of("product", 1L),
                Map.of(
                    "product",
                    com.ai.fabric.integration.discovery.DiscoveryCountMethod.EXACT
                )
            )
        );
        when(sourceAdapter.fetchPage(bundle, "product", null, 100))
            .thenReturn(
                new VectorizationSourcePage(List.of(source), null, false)
            );
        when(recordMapper.map("product", mappingConfig, source)).thenReturn(
            new VectorizationMappedRecord(
                "product",
                "product-1",
                "product-1",
                "v1",
                Map.of("name", "One"),
                Map.of()
            )
        );
        when(platformClient.heartbeat("session-1", "run-1")).thenReturn(
            new VectorizationRunnerPlatformClient.HeartbeatDecision("RUNNING")
        );

        executor.execute(
            "session-1",
            new VectorizationRunnerPlatformClient.ClaimedRun(
                "run-1",
                "BOOTSTRAP",
                "RUNNING",
                "RUNNING"
            )
        );

        verify(targetWriter, never()).upsertBatch(any(), any(), any());
        verify(platformClient).reportCheckpoint(
            eq("session-1"),
            eq("run-1"),
            eq("product"),
            eq("PAGE"),
            any(),
            argThat(progress ->
                progress.path("processedRecords").asInt() == 1
                    && progress.path("succeededRecords").asInt() == 1
                    && progress.path("reconciledRecords").asInt() == 1
            ),
            argThat(details ->
                details.path("submittedRecords").asInt() == 0
                    && details.path("reconciledRecords").asInt() == 1
            )
        );
        verify(platformClient).completeRun(
            eq("session-1"),
            eq("run-1"),
            eq("COMPLETED"),
            any(),
            any(),
            any()
        );
    }

    private VectorizationExecutionBundle bundle(
        ObjectMapper objectMapper,
        ObjectNode executionConfig
    ) {
        ObjectNode mappingConfig = objectMapper.createObjectNode();
        return new VectorizationExecutionBundle(
            "dep-1",
            "run-1",
            VectorizationRunReason.BOOTSTRAP,
            "rev-1",
            VectorizationRunnerMode.PLATFORM_MANAGED_AUTO,
            "vcn-1",
            List.of("product"),
            mappingConfig,
            executionConfig,
            new ConnectionDescriptor(
                "vcn-1",
                "Primary",
                "REST_API",
                "NONE",
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode()
            ),
            new ResolvedSourceAuthMaterial(Map.of(), Map.of()),
            objectMapper.createObjectNode(),
            new TargetConnectionDescriptor(
                "RUNTIME_DATA_SYNC",
                "https://runtime.example",
                "/api/ai/data-sync/batch",
                "/api/ai/data-sync/vector-spaces",
                "X-AIFABRIC-RUNTIME-API-KEY",
                "runtime-secret",
                "/api/admin/indexing/work/{workId}",
                "X-AIFABRIC-RUNTIME-AUTHORIZATION",
                "Bearer private-assertion"
            )
        );
    }
}
