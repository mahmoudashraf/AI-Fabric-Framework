package com.ai.fabric.runtime.workflow;

import static org.assertj.core.api.Assertions.*;
import ai.fabric.intent.orchestration.workflow.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthoringCompletionContractTest {
    final ObjectMapper mapper = new ObjectMapper();
    WorkflowCheckpoint.Step check(boolean requested, boolean changed, String hash) {
        return step("check", "authoring_check_candidate", false, Map.of("draftSaveRequested", requested,
            "hasChanges", changed, "mutationFingerprint", hash));
    }
    WorkflowCheckpoint.Step write(String hash) {
        return step("save", "authoring_update_draft", true, Map.of("status", "EXECUTED", "parameterFingerprint", hash));
    }
    WorkflowCheckpoint.Step step(String id, String action, boolean write, Map<String, Object> result) {
        return new WorkflowCheckpoint.Step(id, action, write, Map.of(), "parameters", WorkflowCheckpoint.Effect.EXECUTED, Map.of("result", result), null);
    }
    WorkflowCheckpoint checkpoint(WorkflowCheckpoint.Step... steps) {
        return new WorkflowCheckpoint("work", "request", "authority", "definition", Instant.now().plusSeconds(60), 0,
            WorkflowAllowance.Usage.empty(), List.of(steps), WorkflowCheckpoint.State.RUNNING, null, List.of(), null);
    }
    @Test void requestedCheckedChangesNeedTheirRealMatchingReceipt() {
        var checked = check(true, true, "candidate");
        assertThat(AuthoringCompletionContract.validate(checkpoint(checked), mapper).code()).isEqualTo("WORKFLOW_CHECKED_CANDIDATE_NOT_SAVED");
        assertThat(AuthoringCompletionContract.validate(checkpoint(checked, write("other")), mapper)).isNotNull();
        assertThat(AuthoringCompletionContract.validate(checkpoint(checked, write("candidate")), mapper)).isNull();
    }
    @Test void previewGuidanceAndAlreadyDesiredStateAreNotForcedToWrite() {
        assertThat(AuthoringCompletionContract.validate(checkpoint(check(false, true, "candidate")), mapper)).isNull();
        assertThat(AuthoringCompletionContract.validate(checkpoint(check(true, false, "candidate")), mapper)).isNull();
        assertThat(AuthoringCompletionContract.validate(checkpoint(), mapper)).isNull();
    }
    @Test void earlierReceiptsCannotSatisfyANewerChangedCandidateButLaterNoOpCanComplete() {
        assertThat(AuthoringCompletionContract.validate(checkpoint(write("candidate"), check(true, true, "candidate")), mapper)).isNotNull();
        assertThat(AuthoringCompletionContract.validate(checkpoint(check(true, true, "candidate"), write("candidate"), check(true, false, "candidate")), mapper)).isNull();
    }
    @Test void anActualReconciledReceiptDoesNotTriggerAnotherWrite() {
        var reconciled = new WorkflowCheckpoint.Step("save", "authoring_update_draft", true, Map.of(), "parameters",
            WorkflowCheckpoint.Effect.EXECUTED, Map.of("status", "EXECUTED", "parameterFingerprint", "candidate"), null);
        assertThat(AuthoringCompletionContract.validate(checkpoint(check(true, true, "candidate"), reconciled), mapper)).isNull();
    }
}
