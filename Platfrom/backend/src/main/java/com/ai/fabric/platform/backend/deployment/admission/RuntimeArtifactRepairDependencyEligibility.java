package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyState;
import java.util.List;
import static org.springframework.http.HttpStatus.CONFLICT;

/** A provisioned but uninitialized dependency is not made READY by an image repair. */
public final class RuntimeArtifactRepairDependencyEligibility {
    private RuntimeArtifactRepairDependencyEligibility() { }
    public static void require(String status, String step, String failureCode, ManagedDependencyState state) {
        if (state == null || state.nodes.size() != 1 || state.retirementFingerprint != null
            || state.runtimeGenerationOperationKey != null || state.runtimeGenerationState != null
            || !state.runtimeGenerationStoppedSlots.isEmpty() || state.operatorRevocationOperationKey != null
            || state.operatorRevocationOperatorId != null || state.operatorRevocationLifecycleKey != null
            || !state.retiredOperatorSecretNames.isEmpty()) fail();
        var node = state.nodes.getFirst();
        if (node.disabled || node.applicationUuid == null || node.applicationUuid.isBlank()
            || node.databaseUuid == null || node.databaseUuid.isBlank() || node.applicationUuid.equals(node.databaseUuid)
            || !node.applicationCreateIntent || !node.databaseCreateIntent || node.baseUrl == null || node.baseUrl.isBlank()
            || node.provisionedConfigurationRevision != state.configurationRevision || node.retirementOperationKey != null
            || !node.volumeReclamationPlan.isEmpty() || !node.reclaimedVolumeUuids.isEmpty()) fail();
        if ("READY".equals(status)) {
            if (!node.initialized || state.effectiveConfigurationRevision != state.configurationRevision) fail();
        } else if (!"BLOCKED".equals(status) || !("INITIALIZE:" + node.slot).equals(step)
            || failureCode == null || failureCode.isBlank() || node.initialized
            || !List.of("PROVISIONED", "INITIALIZING").contains(node.status)
            || state.effectiveConfigurationRevision != -1) fail();
    }
    private static void fail() { throw new AdmissionFailure(CONFLICT, "RUNTIME_ARTIFACT_REPAIR_DEPENDENCY_CHECKPOINT_UNPROVEN"); }
}
