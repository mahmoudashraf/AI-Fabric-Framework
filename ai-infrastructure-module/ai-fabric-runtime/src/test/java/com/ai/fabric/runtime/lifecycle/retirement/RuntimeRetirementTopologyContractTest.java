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
    void acceptsPlatformVectorizationRunnerObligationWithoutDroppingIt() throws Exception {
        String json = """
            {"contractVersion":"LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_V1","target":{"deploymentId":"dep-1","targetProfileId":"target-1","releaseId":"release-1"},"tenantId":"tenant-1","features":{"CHAT_SESSIONS":"PRESENT","CONVERSATION_HANDOVER":"ABSENT","HUMAN_REVIEW":"ABSENT","SPECIALIST_EXECUTION":"ABSENT","SPECIALIST_CHAINS":"ABSENT","SMART_BRAIN":"ABSENT","ACTION_WEBHOOK":"PRESENT","INDEXING":"PRESENT","DOCUMENTS":"PRESENT","SCHEDULER":"ABSENT"},"remoteDependencies":["CONNECTOR","VECTORIZATION_RUNNER"]}
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

        var topology = new RuntimeRetirementTopology(environment, new ObjectMapper(), identity);

        assertThat(topology.remoteObligations(target))
            .extracting(RuntimeTargetRetirementContracts.RemoteObligation::dependencyKind)
            .containsExactly("CONNECTOR", "VECTORIZATION_RUNNER");
    }
}
