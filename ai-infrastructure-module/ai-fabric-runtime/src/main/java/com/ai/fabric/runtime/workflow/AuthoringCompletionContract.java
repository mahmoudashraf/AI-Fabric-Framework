package com.ai.fabric.runtime.workflow;

import ai.fabric.intent.orchestration.workflow.BoundedWorkflowRunner.CompletionFinding;
import ai.fabric.intent.orchestration.workflow.WorkflowCheckpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** A declared draft-save completion needs an actual receipt, not a successful check. */
final class AuthoringCompletionContract {
    private AuthoringCompletionContract() { }

    static CompletionFinding coverage(JsonNode status, boolean previewOnly) {
        var saved = status.path("sourceCoverage");
        if (previewOnly) {
            if (!saved.path("required").asBoolean()) return null;
            var candidate = status.path("lastCandidateCoverage");
            if (candidate.isObject() && candidate.path("complete").asBoolean()
                || !candidate.isObject() && saved.path("complete").asBoolean()) return null;
            return new CompletionFinding("AUTHORING_PREVIEW_COVERAGE_INCOMPLETE",
                "The preview does not yet cover all requested source operations. Read lastCandidateCoverage and correct the preview candidate without saving. Current saved-state coverage is not required for a preview.");
        }
        if (!saved.isObject()) return new CompletionFinding("AUTHORING_COVERAGE_UNAVAILABLE",
            "The current saved-state coverage could not be read. Inspect the current work status before claiming completion.");
        if (saved.path("required").asBoolean() && !saved.path("complete").asBoolean())
            return new CompletionFinding("AUTHORING_SOURCE_COVERAGE_INCOMPLETE",
                "The selected source operations are not all represented by current saved definitions and routes. Read sourceCoverage from authoring_get_work_status, inspect pending operations and decide the next authorized step. Already-correct saved definitions need no new write.");
        return null;
    }

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
        for (int index = checkedIndex + 1; index < checkpoint.steps().size(); index++) {
            var step = checkpoint.steps().get(index);
            if (!"authoring_update_draft".equals(step.action()) || step.effect() != WorkflowCheckpoint.Effect.EXECUTED) continue;
            JsonNode observation = mapper.valueToTree(step.observation());
            var receipt = observation.has("result") ? observation.path("result") : observation;
            // A check is advisory; a later authorized save may refine its input.
            // Exact per-operation replay fingerprints remain enforced by Platform.
            if ("EXECUTED".equals(receipt.path("status").asText())) return null;
        }
        return new CompletionFinding("WORKFLOW_CHECKED_CANDIDATE_NOT_SAVED",
            "The explicit PREPARE draft-save request has a checked, nonempty candidate but no later EXECUTED save receipt. "
                + "A successful validation check is not a saved draft. The requested draft effect is incomplete; decide the next authorized step or report genuinely missing input. No write has been dispatched by this completion check.");
    }
}
