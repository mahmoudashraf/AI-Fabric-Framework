package com.ai.fabric.runtime.workflow;

import ai.fabric.intent.orchestration.workflow.BoundedWorkflowRunner.CompletionFinding;
import ai.fabric.intent.orchestration.workflow.WorkflowCheckpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** A declared draft-save completion needs a real matching receipt, not a successful check. */
final class AuthoringCompletionContract {
    private AuthoringCompletionContract() { }

    static CompletionFinding validate(WorkflowCheckpoint checkpoint, ObjectMapper mapper) {
        JsonNode checked = null;
        int checkedIndex = -1;
        for (int index = 0; index < checkpoint.steps().size(); index++) {
            var step = checkpoint.steps().get(index);
            if ("authoring_check_candidate".equals(step.action()) && step.effect() == WorkflowCheckpoint.Effect.EXECUTED) {
                checked = mapper.valueToTree(step.observation()).path("result");
                checkedIndex = index;
            }
        }
        if (checked == null || !checked.path("draftSaveRequested").asBoolean() || !checked.path("hasChanges").asBoolean()) return null;
        String expected = checked.path("mutationFingerprint").asText();
        for (int index = checkedIndex + 1; index < checkpoint.steps().size(); index++) {
            var step = checkpoint.steps().get(index);
            if (!"authoring_update_draft".equals(step.action()) || step.effect() != WorkflowCheckpoint.Effect.EXECUTED) continue;
            JsonNode observation = mapper.valueToTree(step.observation());
            var receipt = observation.has("result") ? observation.path("result") : observation;
            if (!expected.isEmpty() && "EXECUTED".equals(receipt.path("status").asText())
                && expected.equals(receipt.path("parameterFingerprint").asText())) return null;
        }
        return new CompletionFinding("WORKFLOW_CHECKED_CANDIDATE_NOT_SAVED",
            "The explicit PREPARE draft-save request has a checked, nonempty candidate but no matching EXECUTED save receipt. "
                + "A successful validation check is not a saved draft. The requested draft effect is incomplete; decide the next authorized step or report genuinely missing input. No write has been dispatched by this completion check.");
    }
}
