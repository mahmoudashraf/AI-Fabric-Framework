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
}
