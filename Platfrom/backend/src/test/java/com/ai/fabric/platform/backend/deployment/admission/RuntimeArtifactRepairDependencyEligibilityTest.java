package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RuntimeArtifactRepairDependencyEligibilityTest {
    @ParameterizedTest @ValueSource(strings = {"PROVISIONED", "INITIALIZING"})
    void acceptsExactUninitializedCheckpointWithoutChangingIt(String phase) {
        var state = checkpoint(); state.nodes.getFirst().status = phase;
        var json = new AdmissionJson(new com.fasterxml.jackson.databind.ObjectMapper()); String before = json.write(state);
        RuntimeArtifactRepairDependencyEligibility.require("BLOCKED", "INITIALIZE:inbox", "ACTIVE_ALLOWANCE_EXPIRED", state);
        assertThat(json.write(state)).isEqualTo(before); assertThat(state.nodes.getFirst().initialized).isFalse();
    }
    @Test void doesNotTurnAnUninitializedGraphIntoReady() {
        assertThatThrownBy(() -> RuntimeArtifactRepairDependencyEligibility.require("READY", "READY", null, checkpoint())).isInstanceOf(AdmissionFailure.class);
    }
    @ParameterizedTest @ValueSource(strings = {"PROVISION:inbox", "ADMISSION", "INITIALIZE:other", "QUEUED"})
    void rejectsOtherFailureBoundaries(String phase) {
        assertThatThrownBy(() -> RuntimeArtifactRepairDependencyEligibility.require("BLOCKED", phase, "failed", checkpoint())).isInstanceOf(AdmissionFailure.class);
    }
    @ParameterizedTest @ValueSource(strings = {"configuration", "initialized", "missingApp", "missingDb", "unknownCreate", "retirement", "generation", "reclaimed"})
    void rejectsIncompleteOrCompetingState(String change) {
        var s = checkpoint(); var n = s.nodes.getFirst();
        switch (change) {
            case "configuration" -> n.provisionedConfigurationRevision--;
            case "initialized" -> n.initialized = true;
            case "missingApp" -> n.applicationUuid = null;
            case "missingDb" -> n.databaseUuid = null;
            case "unknownCreate" -> n.databaseCreateIntent = false;
            case "retirement" -> s.retirementFingerprint = "retirement";
            case "generation" -> s.runtimeGenerationOperationKey = "other";
            case "reclaimed" -> n.reclaimedVolumeUuids.add("volume");
        }
        assertThatThrownBy(() -> RuntimeArtifactRepairDependencyEligibility.require("BLOCKED", "INITIALIZE:inbox", "failed", s)).isInstanceOf(AdmissionFailure.class);
    }
    static ManagedDependencyState checkpoint() {
        var s = new ManagedDependencyState(); s.configurationRevision = 2;
        var n = new ManagedDependencyState.Node(); n.slot = "inbox"; n.serviceRef = "owned-inbox";
        n.status = "INITIALIZING"; n.applicationUuid = "owned-app"; n.databaseUuid = "owned-pg";
        n.applicationCreateIntent = true; n.databaseCreateIntent = true; n.baseUrl = "https://owned.example.test";
        n.provisionedConfigurationRevision = 2; s.nodes.add(n); return s;
    }
}
