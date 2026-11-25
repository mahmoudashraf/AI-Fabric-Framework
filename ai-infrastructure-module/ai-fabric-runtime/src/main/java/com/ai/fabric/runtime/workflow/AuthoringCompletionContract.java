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
            return finding("AUTHORING_PREVIEW_COVERAGE_INCOMPLETE",
                "The preview does not yet cover all requested source operations. Read lastCandidateCoverage and correct the preview candidate without saving. Current saved-state coverage is not required for a preview. ",
                coverageDetails(candidate.isObject() ? candidate : saved, "CANDIDATE_NOT_SAVED"));
        }
        if (!saved.isObject()) return new CompletionFinding("AUTHORING_COVERAGE_UNAVAILABLE",
            "The current saved-state coverage could not be read. Inspect the current work status before claiming completion.");
        if (saved.path("required").asBoolean() && !saved.path("complete").asBoolean())
            return finding("AUTHORING_SOURCE_COVERAGE_INCOMPLETE",
                "The selected source operations are not all represented by current saved definitions and routes. Correct the pending mappings using the source documentation and resource contracts, then save authorized changes with authoring_update_draft. Already-correct saved definitions need no new write. ",
                coverageDetails(saved, "CURRENT_SAVED_CONFIGURATION"));
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
        if (checked.path("requiresCorrection").asBoolean())
            return finding("WORKFLOW_CANDIDATE_REQUIRES_CORRECTION",
                "The latest candidate was not saved and has validation findings. Repair the candidate using the authorized contracts; a rejected candidate must not be saved merely to finish. "
                    + "If the requested change is incompatible with required settings the user asked to preserve, return UNSUPPORTED with a clear dependency explanation and no write. "
                    + "Use NEEDS_INPUT only for genuinely missing business facts or authorization. Do not claim the draft was saved. Validation details (data): ",
                findingsDetails(checked.path("findings")));
        return finding("WORKFLOW_CHECKED_CANDIDATE_NOT_SAVED",
            "The explicit PREPARE draft-save request has a checked, nonempty candidate but no later EXECUTED save receipt. "
                + "A successful validation check is not a saved draft. If the candidate is ready, call authoring_update_draft with the requested changes; otherwise correct the pending mappings first. "
                + "Do not repeat COMPLETE or an unchanged check instead of the missing authorized save. No write has been dispatched by this completion check. ",
            coverageDetails(checked.path("sourceCoverage"), "CANDIDATE_NOT_SAVED"));
    }

    private static CompletionFinding finding(String code, String instruction, JsonNode details) {
        // The SDK bounds completion feedback at 2000 characters; preserve instructions and mark omitted data.
        String encoded = details.toString(), omitted = " ... [truncated; read current context or work status for full details]";
        int available = 2000 - instruction.length();
        if (encoded.length() > available) encoded = encoded.substring(0, available - omitted.length()) + omitted;
        return new CompletionFinding(code, instruction + encoded);
    }

    private static JsonNode findingsDetails(JsonNode findings) {
        var details = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        for (String field : java.util.List.of("valid", "publishReady", "errorCount"))
            if (findings.path(field).isValueNode()) details.set(field, findings.path(field));
        var issues = details.putArray("issues");
        for (var issue : findings.path("issues")) {
            if (issues.size() == 6) break;
            var item = issues.addObject();
            for (String field : java.util.List.of("severity", "code", "path", "message"))
                copyText(issue, item, field, "message".equals(field) ? 512 : 200);
        }
        return details;
    }

    // Feedback is a bounded view of authorized observations, never a write or a new completion rule.
    private static JsonNode coverageDetails(JsonNode coverage, String evidence) {
        var details = com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("evidence", evidence);
        if (!coverage.isObject()) return details;
        details.put("complete", coverage.path("complete").asBoolean())
            .put("completed", coverage.path("completed").asInt()).put("total", coverage.path("total").asInt());
        var pending = details.putArray("pending");
        for (var operation : coverage.path("items")) {
            if (!"PENDING".equals(operation.path("state").asText())) continue;
            if (pending.size() == 4) { details.put("pendingTruncated", true); break; }
            var item = pending.addObject();
            for (String field : java.util.List.of("operationId", "label")) copyText(operation, item, field, 200);
            var candidates = item.putArray("candidates");
            for (var candidate : operation.path("savedCandidates")) {
                if (candidates.size() == 2) break;
                var mapped = candidates.addObject(); copyText(candidate, mapped, "actionName", 200);
                var missing = mapped.putArray("missingRequiredMappings");
                for (var mapping : candidate.path("missingRequiredMappings")) {
                    if (missing.size() == 6) { mapped.put("mappingsTruncated", true); break; }
                    var entry = missing.addObject();
                    for (String field : java.util.List.of("location", "name", "code")) copyText(mapping, entry, field, 128);
                }
            }
        }
        return details;
    }

    private static void copyText(JsonNode source, com.fasterxml.jackson.databind.node.ObjectNode target, String field, int max) {
        if (!source.path(field).isTextual()) return;
        String text = source.path(field).textValue();
        target.put(field, text.substring(0, Math.min(text.length(), max)));
    }
}
