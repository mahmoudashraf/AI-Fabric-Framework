package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeRetirementTopologyPlanServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RuntimeRetirementTopologyPlanService service = new RuntimeRetirementTopologyPlanService(mapper);

    @Test
    void producesDeterministicCompleteReleaseBoundContractWithConservativeDefaults() throws Exception {
        var first = service.plan(deployment(), release(), Map.of(), true, true);
        var second = service.plan(deployment(), release(), Map.of(), true, true);

        assertThat(second).isEqualTo(first);
        assertThat(first.sha256()).isEqualTo(HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(first.json().getBytes(StandardCharsets.UTF_8))
        ));
        var json = mapper.readTree(first.json());
        assertThat(json.path("contractVersion").asText()).isEqualTo("LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_V2");
        assertThat(json.path("target").path("deploymentId").asText()).isEqualTo("dep-1");
        assertThat(json.path("target").path("targetProfileId").asText()).isEqualTo("target-staging");
        assertThat(json.path("target").path("releaseId").asText()).isEqualTo("release-1");
        assertThat(json.path("tenantId").asText()).isEqualTo("tenant-1");
        assertThat(json.path("features").size()).isEqualTo(10);
        json.path("features").elements().forEachRemaining(value -> assertThat(value.asText()).isEqualTo("PRESENT"));
        assertThat(json.path("remoteDependencies").size()).isEqualTo(3);
        assertThat(json.path("remoteDependencies").toString())
            .contains("CONNECTOR", "HUMAN_INBOX", "VECTORIZATION_RUNNER");
    }

    @Test
    void onlyExactFalseMarksAFeatureAbsentAndRemovesUnneededInboxDependency() throws Exception {
        var plan = service.plan(deployment(), release(), Map.of(
            "LOOMAI_CONVERSATION_HANDOVER_ENABLED", "false",
            "AI_INDEXING_ENABLED", " FALSE ",
            "AI_CHAT_ENABLED", "true",
            "LOOMAI_ACTION_WEBHOOK_ENABLED", "false"
        ), false);

        var json = mapper.readTree(plan.json());
        assertThat(json.path("features").path("CONVERSATION_HANDOVER").asText()).isEqualTo("ABSENT");
        assertThat(json.path("features").path("INDEXING").asText()).isEqualTo("ABSENT");
        assertThat(json.path("features").path("CHAT_SESSIONS").asText()).isEqualTo("PRESENT");
        assertThat(json.path("features").path("DOCUMENTS").asText()).isEqualTo("PRESENT");
        assertThat(json.path("features").path("ACTION_WEBHOOK").asText()).isEqualTo("PRESENT");
        assertThat(json.path("remoteDependencies").isEmpty()).isTrue();
    }

    @Test
    void rejectsCrossDeploymentReleaseIdentity() {
        var release = release();
        release.setDeploymentId("foreign-deployment");

        assertThatThrownBy(() -> service.plan(deployment(), release, Map.of(), false))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ownership");
    }

    private DeploymentEntity deployment() {
        var deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setTenantId("tenant-1");
        return deployment;
    }

    private DeploymentReleaseEntity release() {
        var release = new DeploymentReleaseEntity();
        release.setId("release-1");
        release.setDeploymentId("dep-1");
        release.setTargetProfileId("target-staging");
        return release;
    }
}
