package com.ai.fabric.platform.backend.deployment.operations.lifecycle.runtimeadapter;

import static org.assertj.core.api.Assertions.*;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeDrainEnvironmentPlanTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RuntimeDrainEnvironmentPlan planner = new RuntimeDrainEnvironmentPlan(mapper);
    private final DeploymentEntity deployment = new DeploymentEntity();
    private final DeploymentVersionEntity version = new DeploymentVersionEntity();
    private final DeploymentReleaseEntity release = new DeploymentReleaseEntity();

    RuntimeDrainEnvironmentPlanTest() {
        deployment.setId("dep-1"); deployment.setTenantId("tenant-1");
        version.setId("ver-1"); version.setDeploymentId("dep-1");
        version.setProviderConfigJson("{\"operationalDrainEnabled\":true}");
        version.setEntityConfigJson("{\"ai-entities\":{\"document\":{\"indexing\":{\"enabled\":true}}}}");
        release.setId("rel-1"); release.setDeploymentId("dep-1"); release.setDeploymentVersionId("ver-1");
        release.setTargetProfileId("target-1"); release.setRuntimeAuthoritySnapshotId("snapshot-1");
        release.setRuntimeAuthorityFingerprint("a".repeat(64));
    }

    @Test
    void defaultAndFalseNeverIntroduceDrainOrStoragePrerequisitesOnColdDeploy() {
        for (String config : new String[]{null, "{}", "{\"operationalDrainEnabled\":false}", "{\"operationalDrainEnabled\":\"true\"}"}) {
            version.setProviderConfigJson(config);
            assertThat(planner.plan(deployment, version, release, Map.of(), false, false, false, null)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "LOOMAI_RUNTIME_TARGET_RETIREMENT_ENABLED", "false", "LOOMAI_RUNTIME_TARGET_RETIREMENT_DEDICATED_STORE", "false"));
        }
    }

    @Test
    void explicitOptInUsesExactReleaseTargetAndRealDeclaredRemoteObligations() throws Exception {
        var environment = environment();
        environment.put("LOOMAI_CONVERSATION_HANDOVER_ENABLED", "true");
        var plan = planner.plan(deployment, version, release, environment, true, true, true, artifactManifest());
        assertThat(plan).containsEntry("LOOMAI_RUNTIME_TARGET_RETIREMENT_ENABLED", "true")
            .containsEntry("LOOMAI_RUNTIME_TARGET_RETIREMENT_DEDICATED_STORE", "true")
            .containsEntry("AI_EXECUTION_ASYNC_REPOSITORY", "JDBC");
        String raw = plan.get("LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_JSON");
        var topology = mapper.readTree(raw);
        assertThat(topology.path("target").path("releaseId").asText()).isEqualTo("rel-1");
        assertThat(topology.path("tenantId").asText()).isEqualTo("tenant-1");
        assertThat(topology.path("remoteDependencies").toString()).isEqualTo("[\"CONNECTOR\",\"VECTORIZATION_RUNNER\",\"HUMAN_INBOX\"]");
        assertThat(plan.get("LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_SHA256")).isEqualTo(HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))));
        assertThat(planner.plan(deployment, version, release, new java.util.TreeMap<>(environment), true, true, true, artifactManifest())).isEqualTo(plan);
    }

    @Test
    void runtimeOwnedDocumentCanaryHasNoInventedRemoteDependencyAndPreservesTypedVectorScope() throws Exception {
        var environment = environment();
        environment.put("AI_VECTOR_DB_TYPE", "pinecone");
        environment.put("AI_PROVIDERS_PINECONE_NAMESPACE_PREFIX", "dep-1-target-1");
        var plan = planner.plan(deployment, version, release, environment, true, false, false, artifactManifest());
        var topology = mapper.readTree(plan.get("LOOMAI_RUNTIME_RETIREMENT_TOPOLOGY_JSON"));
        assertThat(topology.path("remoteDependencies").isEmpty()).isTrue();
        assertThat(topology.path("features").path("INDEXING").asText()).isEqualTo("PRESENT");
        assertThat(topology.path("features").path("SPECIALIST_EXECUTION").asText()).isEqualTo("PRESENT");
        assertThat(topology.path("vectorScope").path("scopeType").asText()).isEqualTo("NAMESPACE_PREFIX");
        assertThat(topology.path("vectorScope").path("scopeValue").asText()).isEqualTo("dep-1-target-1");
        assertThat(topology.path("vectorScope").path("entityTypes").get(0).asText()).isEqualTo("document");
    }

    @Test
    void optInRefusesUnownedUnattestedOrUnconfiguredState() {
        assertThatThrownBy(() -> planner.plan(deployment, version, release, environment(), false, false, false, artifactManifest()))
            .hasMessageContaining("dedicated target storage");
        release.setRuntimeAuthorityFingerprint(null);
        assertThatThrownBy(() -> planner.plan(deployment, version, release, environment(), true, false, false, artifactManifest()))
            .hasMessageContaining("frozen authority");
        release.setRuntimeAuthorityFingerprint("a".repeat(64));
        assertThatThrownBy(() -> planner.plan(deployment, version, release, Map.of(), true, false, false, artifactManifest()))
            .hasMessageContaining("JDBC specialist secrets");
        release.setDeploymentId("other");
        assertThatThrownBy(() -> planner.plan(deployment, version, release, environment(), true, false, false, artifactManifest()))
            .hasMessageContaining("frozen authority");
    }

    @Test
    void missingVectorPrefixOrFrozenEntityInventoryNeverBecomesCoverage() {
        var environment = environment(); environment.put("AI_VECTOR_DB_TYPE", "pinecone");
        assertThatThrownBy(() -> planner.plan(deployment, version, release, environment, true, false, false, artifactManifest()))
            .hasMessageContaining("exact target vector scope");
        version.setEntityConfigJson("{}");
        assertThatThrownBy(() -> planner.plan(deployment, version, release, environment(), true, false, false, artifactManifest()))
            .hasMessageContaining("frozen indexed entities");
    }

    @Test
    void anOldPinnedArtifactCannotEnableTheNewReleaseScopedContract() {
        assertThatThrownBy(() -> planner.plan(deployment, version, release, environment(), true, false, false,
            "{\"capabilities\":[\"runtime-cold-maintenance-generation-adoption-v1\"]}"))
            .hasMessageContaining("pinned release-scoped Runtime artifact with V24");
        assertThatThrownBy(() -> planner.plan(deployment, version, release, environment(), true, false, false, null))
            .hasMessageContaining("pinned release-scoped Runtime artifact with V24");
    }

    private String artifactManifest() {
        return "{\"capabilities\":[\"runtime-release-scoped-drain-v1\"],\"migrationIds\":[\"runtime-release-scoped-retirement-v24\"]}";
    }

    private LinkedHashMap<String, String> environment() {
        var env = new LinkedHashMap<String, String>();
        env.put("AI_EXECUTION_ASYNC_ENCRYPTION_SECRET", "e".repeat(64));
        env.put("AI_EXECUTION_ASYNC_FINGERPRINT_SECRET", "f".repeat(64));
        env.put("LOOMAI_CONVERSATION_HANDOVER_ENABLED", "false");
        env.put("AI_EXECUTION_REVIEWS_ENABLED", "false");
        env.put("AI_EXECUTION_SPECIALIST_CHAINS_ENABLED", "false");
        env.put("LOOMAI_SMART_BRAIN_ENABLED", "false");
        return env;
    }
}
