package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Metric;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Snapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Restart eligibility only. Terminal failure history is retained, not relabelled as a successful drain. */
final class RuntimeArtifactRepairIdleCheckpoint {
    private static final Set<String> RESTART_ONLY_BLOCKERS = Set.of(
        "FRAMEWORK_EXECUTION_RECOVERY_TARGET_EPOCH_FENCE_UNSUPPORTED:SPECIALIST_EXECUTION",
        "FRAMEWORK_INDEXING_PROCESSOR_TARGET_EPOCH_FENCE_UNSUPPORTED", "DRAIN_WORK_REMAINS");
    record Queue(long commitPending, long pending, long processing, long completed, long superseded, long deadLetters) { }

    static Queue require(Snapshot snapshot, JsonNode overview) {
        if (snapshot == null || !snapshot.inventoryComplete() || snapshot.inFlightRequests() != 0
            || snapshot.blockers() == null || snapshot.blockers().stream().anyMatch(code -> !RESTART_ONLY_BLOCKERS.contains(code))
            || snapshot.topologyFingerprint() == null || snapshot.topologyFingerprint().isBlank()
            || snapshot.evidenceReference() == null || snapshot.evidenceReference().isBlank()) fail();
        zero(snapshot.openConversations()); zero(snapshot.pendingConfirmations()); zero(snapshot.humanWork());
        if (overview == null || !overview.path("success").isBoolean() || !overview.path("success").booleanValue()
            || !overview.path("queue").path("ready").isBoolean() || !overview.path("queue").path("ready").booleanValue()) fail();
        var values = overview.path("queue");
        var queue = new Queue(count(values, "commitPending"), count(values, "pending"), count(values, "processing"),
            count(values, "completed"), count(values, "superseded"), count(values, "deadLetters"));
        if (queue.commitPending() != 0 || queue.pending() != 0 || queue.processing() != 0) fail();
        if (snapshot.blockers().contains("DRAIN_WORK_REMAINS") && queue.deadLetters() == 0) fail();
        var background = snapshot.backgroundWork();
        if (background == null || !background.complete() || background.count() == null
            || background.count() != queue.deadLetters() || background.observations() == null) fail();
        var sources = new HashSet<String>();
        boolean indexingObserved = false;
        for (var observation : background.observations()) {
            if (observation == null || !observation.complete() || observation.source() == null
                || !sources.add(observation.source()) || observation.observedCount() == null
                || observation.blockers() == null || !observation.blockers().isEmpty()) fail();
            if ("ai_indexing_queue".equals(observation.source())) {
                if (observation.observedCount() != queue.deadLetters()) fail();
                indexingObserved = true;
            } else if (observation.observedCount() != 0) fail();
        }
        if (!indexingObserved) fail();
        return queue;
    }

    private static void zero(Metric metric) {
        if (metric == null || !metric.complete() || metric.count() == null || metric.count() != 0
            || metric.observations() == null || metric.observations().isEmpty()) fail();
        for (var observation : metric.observations()) if (observation == null || !observation.complete()
            || observation.observedCount() == null || observation.observedCount() != 0
            || observation.blockers() == null || !observation.blockers().isEmpty()) fail();
    }

    private static long count(JsonNode values, String name) {
        var value = values.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) fail();
        return value.longValue();
    }
    private static void fail() { throw new AdmissionFailure(CONFLICT, "RUNTIME_ARTIFACT_REPAIR_IDLE_CHECKPOINT_REQUIRED"); }
}
