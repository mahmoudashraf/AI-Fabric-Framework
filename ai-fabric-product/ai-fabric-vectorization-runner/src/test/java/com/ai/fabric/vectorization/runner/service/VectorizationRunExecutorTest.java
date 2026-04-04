package com.ai.fabric.vectorization.runner.service;

import com.ai.fabric.integration.connection.ConnectionDescriptor;
import com.ai.fabric.integration.credential.ResolvedSourceAuthMaterial;
import com.ai.fabric.vectorization.adapter.source.VectorizationSourceAdapter;
import com.ai.fabric.vectorization.mapping.VectorizationRecordMapper;
import com.ai.fabric.vectorization.model.TargetConnectionDescriptor;
import com.ai.fabric.vectorization.model.VectorizationDiscoveryResult;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationRunReason;
import com.ai.fabric.vectorization.model.VectorizationRunnerMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
}
