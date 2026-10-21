package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Metric;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Snapshot;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import com.ai.fabric.platform.backend.deployment.service.RuntimeColdStoreReadback;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Restart eligibility only. Terminal failure history is retained, not relabelled as a successful drain. */
final class RuntimeArtifactRepairIdleCheckpoint {
    private static final Set<String> RESTART_ONLY_BLOCKERS = Set.of(
        "FRAMEWORK_EXECUTION_RECOVERY_TARGET_EPOCH_FENCE_UNSUPPORTED:SPECIALIST_EXECUTION",
        "FRAMEWORK_INDEXING_PROCESSOR_TARGET_EPOCH_FENCE_UNSUPPORTED", "DRAIN_WORK_REMAINS");
    record Queue(long commitPending, long pending, long processing, long completed, long superseded, long deadLetters) { }
    private static final Map<String, String> EMPTY_JDBC_OBSERVATIONS = Map.of(
        "chat_sessions.session_metadata.actionDraft", "ACTION_DRAFT_STORE_ENUMERATION_UNSUPPORTED",
        "ai_conversation_orchestration_state.actionDraft", "CANONICAL_ACTION_DRAFT_STORE_ENUMERATION_UNSUPPORTED",
        "ai_specialist_execution", "DRAIN_PROVIDER_UNSUPPORTED:ai_specialist_execution");

    static Queue requireWithEmptyJdbcStores(Snapshot snapshot, JsonNode overview, RuntimeColdStoreReadback.Proof proof) {
        if (proof == null || proof.reference() == null || proof.reference().isBlank()
            || proof.counts() == null || !proof.counts().keySet().equals(Set.of("chat_sessions", "ai_conversation_control",
                "ai_conversation_orchestration_state", "ai_specialist_execution"))
            || proof.counts().values().stream().anyMatch(count -> count == null || count != 0)
            || snapshot == null || snapshot.inventoryComplete() || snapshot.inFlightRequests() != 0
            || snapshot.blockers() == null || snapshot.blockers().stream().anyMatch(code ->
                !RESTART_ONLY_BLOCKERS.contains(code) && !EMPTY_JDBC_OBSERVATIONS.containsValue(code))
            || snapshot.topologyFingerprint() == null || snapshot.evidenceReference() == null) fail();
        zero(snapshot.openConversations()); zero(snapshot.pendingConfirmations()); zero(snapshot.humanWork());
        var background = snapshot.backgroundWork();
        if (background == null || background.complete() || background.count() != null || background.observations() == null) fail();
        var sources = new HashSet<String>();
        var verified = new java.util.ArrayList<com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Observation>();
        for (var observation : background.observations()) {
            if (observation == null || observation.source() == null || !sources.add(observation.source())
                || observation.observedCount() == null || observation.blockers() == null) fail();
            if (!observation.complete()) {
                String expected = EMPTY_JDBC_OBSERVATIONS.get(observation.source());
                if (expected == null || observation.observedCount() != 0 || !observation.blockers().equals(java.util.List.of(expected))) fail();
                verified.add(new com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Observation(
                    observation.source(), 0L, true, java.util.List.of()));
            } else verified.add(observation);
        }
        // Validate the combined evidence without altering or relabelling the original runtime snapshot.
        var combined = new Snapshot(snapshot.snapshotId(), proof.reference(), snapshot.target(), snapshot.observedAt(),
            snapshot.openConversations(), snapshot.pendingConfirmations(), snapshot.humanWork(),
            new com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Metric(
                verified.stream().mapToLong(value -> value.observedCount()).sum(), true, verified),
            snapshot.inFlightRequests(), true, snapshot.workerFencingComplete(), false, false, snapshot.deadlineElapsed(),
            snapshot.topologyFingerprint(), snapshot.remoteObligations(), snapshot.blockers().stream()
                .filter(code -> !EMPTY_JDBC_OBSERVATIONS.containsValue(code)).toList());
        return require(combined, overview);
    }

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
