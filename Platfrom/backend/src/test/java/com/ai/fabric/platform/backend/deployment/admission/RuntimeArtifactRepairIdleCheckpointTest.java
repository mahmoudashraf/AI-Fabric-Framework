package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class RuntimeArtifactRepairIdleCheckpointTest {
    final ObjectMapper mapper = new ObjectMapper();

    @Test void permitsOnlyAnIdleRestartWhileRetainingUnsupportedFencingAndTerminalFailures() {
        var snapshot = snapshot("valid"); var queue = overview();
        var proof = RuntimeArtifactRepairIdleCheckpoint.require(snapshot, queue);
        assertThat(proof.completed()).isEqualTo(36); assertThat(proof.deadLetters()).isEqualTo(36);
        assertThat(snapshot.workerFencingComplete()).isFalse(); assertThat(snapshot.localDrainComplete()).isFalse();
        assertThat(snapshot.blockers()).contains("FRAMEWORK_INDEXING_PROCESSOR_TARGET_EPOCH_FENCE_UNSUPPORTED", "DRAIN_WORK_REMAINS");
        assertThat(queue.path("queue").path("deadLetters").longValue()).isEqualTo(36);
    }

    @ParameterizedTest @ValueSource(strings = {"commitPending", "pending", "processing"})
    void activeOrUncommittedIndexWorkPreventsColdRestart(String field) {
        var value = overview(); ((ObjectNode) value.path("queue")).put(field, 1);
        assertThatThrownBy(() -> RuntimeArtifactRepairIdleCheckpoint.require(snapshot("valid"), value)).isInstanceOf(AdmissionFailure.class);
    }

    @Test void unexplainedDrainWorkBlockerCannotBeIgnoredWhenEveryCountIsZero() {
        var zero = metric("empty", 0, true); var value = overview();
        ((ObjectNode) value.path("queue")).put("deadLetters", 0);
        var contradictory = new Snapshot("snapshot", "evidence", null, Instant.now(), zero, zero, zero,
            metric("ai_indexing_queue", 0, true), 0, true, false, false, false, false,
            "topology", List.of(), List.of("DRAIN_WORK_REMAINS"));
        assertThatThrownBy(() -> RuntimeArtifactRepairIdleCheckpoint.require(contradictory, value)).isInstanceOf(AdmissionFailure.class);
    }

    @ParameterizedTest @ValueSource(strings = {"commitPending", "pending", "processing", "completed", "superseded", "deadLetters"})
    void missingOrCoercedCountsAreNotZero(String field) {
        var value = overview(); ((ObjectNode) value.path("queue")).remove(field);
        assertThatThrownBy(() -> RuntimeArtifactRepairIdleCheckpoint.require(snapshot("valid"), value)).isInstanceOf(AdmissionFailure.class);
        ((ObjectNode) value.path("queue")).put(field, "0");
        assertThatThrownBy(() -> RuntimeArtifactRepairIdleCheckpoint.require(snapshot("valid"), value)).isInstanceOf(AdmissionFailure.class);
    }

    @ParameterizedTest @ValueSource(strings = {"unknown-blocker", "incomplete", "conversation", "confirmation", "human",
        "inflight", "active-background", "unknown-provider", "duplicate-source", "missing-indexing", "dead-letter-mismatch"})
    void unknownOrActiveWorkCannotBeHiddenByTheRestartMode(String change) {
        assertThatThrownBy(() -> RuntimeArtifactRepairIdleCheckpoint.require(snapshot(change), overview())).isInstanceOf(AdmissionFailure.class);
    }

    ObjectNode overview() {
        var value = mapper.createObjectNode().put("success", true);
        value.putObject("queue").put("ready", true).put("commitPending", 0).put("pending", 0).put("processing", 0)
            .put("completed", 36).put("superseded", 0).put("deadLetters", 36);
        return value;
    }
    Snapshot snapshot(String change) {
        var zero = metric("empty", 0, true); var active = metric("active", 1, true);
        var observed = new Observation("ai_indexing_queue", change.equals("dead-letter-mismatch") ? 35L : 36L, true, List.of());
        List<Observation> observations = switch (change) {
            case "active-background" -> List.of(observed, new Observation("ai_specialist_execution", 1L, true, List.of()));
            case "unknown-provider" -> List.of(observed, new Observation("ai_specialist_execution", 0L, false, List.of("DRAIN_PROVIDER_UNSUPPORTED:ai_specialist_execution")));
            case "duplicate-source" -> List.of(observed, observed);
            case "missing-indexing" -> List.of(new Observation("unknown", 36L, true, List.of()));
            default -> List.of(observed, new Observation("ai_specialist_execution", 0L, true, List.of()));
        };
        return new Snapshot("snapshot", "evidence", null, Instant.now(), change.equals("conversation") ? active : zero,
            change.equals("confirmation") ? active : zero, change.equals("human") ? active : zero,
            new Metric(36L, true, observations), change.equals("inflight") ? 1 : 0, !change.equals("incomplete"), false, false, false, false,
            "topology", List.of(), change.equals("unknown-blocker") ? List.of("UNKNOWN_WORKER") : List.of(
                "FRAMEWORK_EXECUTION_RECOVERY_TARGET_EPOCH_FENCE_UNSUPPORTED:SPECIALIST_EXECUTION",
                "FRAMEWORK_INDEXING_PROCESSOR_TARGET_EPOCH_FENCE_UNSUPPORTED", "DRAIN_WORK_REMAINS"));
    }
    Metric metric(String source, long count, boolean complete) {
        return new Metric(count, complete, List.of(new Observation(source, count, complete, List.of())));
    }
}
