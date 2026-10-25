package com.ai.fabric.runtime.lifecycle.retirement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ai.fabric.runtime.lifecycle.retirement.RuntimeTargetRetirementContracts.Target;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RuntimeRetirementTopologyContractTest {

    @Test
    void indexingAndSpecialistCapabilitiesRequireEveryInstalledBoundaryNotJustWorkerPresence() throws Exception {
        var mapper = new ObjectMapper();
        var identity = new RuntimeTargetRetirementIdentity(true, "dep-1", "target-1", "release-1", "tenant-1", true);
        var features = new java.util.EnumMap<RuntimeRetirementTopology.Feature, RuntimeRetirementTopology.Presence>(RuntimeRetirementTopology.Feature.class);
        for (var feature : RuntimeRetirementTopology.Feature.values()) features.put(feature, RuntimeRetirementTopology.Presence.ABSENT);
        features.put(RuntimeRetirementTopology.Feature.CHAT_SESSIONS, RuntimeRetirementTopology.Presence.PRESENT);
        features.put(RuntimeRetirementTopology.Feature.INDEXING, RuntimeRetirementTopology.Presence.PRESENT);
        features.put(RuntimeRetirementTopology.Feature.SPECIALIST_EXECUTION, RuntimeRetirementTopology.Presence.PRESENT);
        var contract = new RuntimeRetirementTopology.Contract(RuntimeRetirementTopology.VERSION, identity.target(), "tenant-1",
            features, java.util.List.of(), new RuntimeRetirementTopology.VectorScope("pinecone", "NAMESPACE_PREFIX", "dep-1-target-1", java.util.List.of("document")));
        String raw = mapper.writeValueAsString(contract);
        var environment = new MockEnvironment().withProperty("loomai.runtime.target-retirement.topology-json", raw)
            .withProperty("loomai.runtime.target-retirement.topology-sha256", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))));
        for (String key : java.util.List.of("loomai.conversation-handover.enabled", "ai.execution.reviews.enabled",
            "ai.execution.specialist-chains.enabled", "loomai.smart-brain.enabled", "loomai.documents.enabled")) environment.withProperty(key, "false");
        var topology = new RuntimeRetirementTopology(environment, mapper, identity);
        var coverage = new RuntimeRetirementFencingCoverage();
        coverage.installed("runtime-ingress");
        coverage.installed("AsyncIndexingWorker.run");
        assertThat(topology.fencingBlockers(true, coverage)).contains(
            "FRAMEWORK_INDEXING_PROCESSOR_TARGET_EPOCH_FENCE_UNSUPPORTED",
            "FRAMEWORK_EXECUTION_RECOVERY_TARGET_EPOCH_FENCE_UNSUPPORTED:SPECIALIST_EXECUTION");
        for (String boundary : java.util.List.of("aiFabricExecutionTaskExecutor.attempts", "IndexingQueueService.enqueue",
            "IndexingQueueService.enqueueForSynchronousDispatch", "IndexingQueueService.lease", "IndexingQueueService.claimSynchronous",
            "IndexingQueueService.markCompleted", "IndexingQueueService.markFailure", "IndexingQueueService.markSuperseded",
            "IndexingWorkProcessor.process", "BatchIndexingWorker.run", "SyncIndexingRetryWorker.run",
            "IndexingCleanupScheduler.reclaimStuckEntries", "IndexingCleanupScheduler.purgeOldEntries")) coverage.installed(boundary);
        assertThat(topology.fencingBlockers(true, coverage)).isEmpty();
    }

    @Test
    void canonicalConversationTopologyRequiresOrchestrationStateFenceCoverage() {
        var f = new RuntimeRetirementTestFixture(true, true, java.util.List.of(), true);
        var coverage = new RuntimeRetirementFencingCoverage();
        coverage.installed("conversation-control");
        coverage.installed("RuntimeConversationHandoverService.scheduledMaintenance");

        assertThat(f.topology.fencingBlockers(true, coverage)).contains("HANDOVER_LOCAL_EFFECT_FENCE_UNVERIFIED");

        coverage.installed("conversation-orchestration-state");
        assertThat(f.topology.fencingBlockers(true, coverage)).isEmpty();
    }

    @Test
    void acceptsPlatformVectorizationRunnerObligationWithoutDroppingIt() throws Exception {
        String json = """
            {"contractVersion":"LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_V2","target":{"deploymentId":"dep-1","targetProfileId":"target-1","releaseId":"release-1"},"tenantId":"tenant-1","features":{"CHAT_SESSIONS":"PRESENT","CONVERSATION_HANDOVER":"ABSENT","HUMAN_REVIEW":"ABSENT","SPECIALIST_EXECUTION":"ABSENT","SPECIALIST_CHAINS":"ABSENT","SMART_BRAIN":"ABSENT","ACTION_WEBHOOK":"PRESENT","INDEXING":"PRESENT","DOCUMENTS":"PRESENT","SCHEDULER":"ABSENT"},"remoteDependencies":["CONNECTOR","VECTORIZATION_RUNNER"],"vectorScope":{"provider":"qdrant","scopeType":"COLLECTION_PREFIX","scopeValue":"dep-1-target-1","entityTypes":["dealer-vehicle","document"]}}
            """.trim();
        String digest = HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8))
        );
        var environment = new MockEnvironment()
            .withProperty("loomai.runtime.target-retirement.topology-json", json)
            .withProperty("loomai.runtime.target-retirement.topology-sha256", digest)
            .withProperty("loomai.conversation-handover.enabled", "false")
            .withProperty("ai.execution.reviews.enabled", "false")
            .withProperty("ai.execution.enabled", "false")
            .withProperty("ai.execution.specialist-chains.enabled", "false")
            .withProperty("loomai.smart-brain.enabled", "false");
        var identity = mock(RuntimeTargetRetirementIdentity.class);
        Target target = new Target("dep-1", "target-1", "release-1");
        when(identity.target()).thenReturn(target);
        when(identity.tenantId()).thenReturn("tenant-1");
        when(identity.dedicatedStore()).thenReturn(true);

        var topology = new RuntimeRetirementTopology(environment, new ObjectMapper(), identity);

        assertThat(topology.remoteObligations(target))
            .extracting(RuntimeTargetRetirementContracts.RemoteObligation::dependencyKind)
            .containsExactly("CONNECTOR", "VECTORIZATION_RUNNER");
    }
}
