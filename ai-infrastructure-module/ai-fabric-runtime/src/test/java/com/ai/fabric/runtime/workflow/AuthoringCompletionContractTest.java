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
    @Test void requestedCheckedChangesNeedAnActualSaveButMayBeRefinedBeforeSaving() {
        var checked = check(true, true, "candidate");
        assertThat(AuthoringCompletionContract.validate(checkpoint(checked), mapper).code()).isEqualTo("WORKFLOW_CHECKED_CANDIDATE_NOT_SAVED");
        assertThat(AuthoringCompletionContract.validate(checkpoint(checked, write("refined-candidate")), mapper)).isNull();
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
    @Test void failedUncertainOrUnexecutedWritesDoNotSatisfyCompletion() {
        for (var effect : List.of(WorkflowCheckpoint.Effect.REJECTED, WorkflowCheckpoint.Effect.OUTCOME_UNKNOWN,
            WorkflowCheckpoint.Effect.DISPATCHING)) {
            var incomplete = new WorkflowCheckpoint.Step("save", "authoring_update_draft", true, Map.of(), "parameters",
                effect, Map.of("result", Map.of("status", "EXECUTED")), null);
            assertThat(AuthoringCompletionContract.validate(checkpoint(check(true, true, "candidate"), incomplete), mapper)).isNotNull();
        }
        var failedReceipt = step("save", "authoring_update_draft", true, Map.of("status", "FAILED"));
        assertThat(AuthoringCompletionContract.validate(checkpoint(check(true, true, "candidate"), failedReceipt), mapper)).isNotNull();
    }
    @Test void rejectedCandidatesExplainTheirFindingsInsteadOfDemandingAnImpossibleSave() {
        var checked = step("check", "authoring_check_candidate", false, Map.of(
            "draftSaveRequested", true, "hasChanges", true, "requiresCorrection", true,
            "findings", Map.of("valid", false, "errorCount", 1, "issues", List.of(Map.of(
                "severity", "ERROR", "code", "REQUIRED_SOURCE_DISABLED", "path", "/knowledgeSourceConfig/sources",
                "message", "The retained specialist requires this enabled source.")))));
        var finding = AuthoringCompletionContract.validate(checkpoint(checked), mapper);
        assertThat(finding.code()).isEqualTo("WORKFLOW_CANDIDATE_REQUIRES_CORRECTION");
        assertThat(finding.detail()).contains("REQUIRED_SOURCE_DISABLED", "UNSUPPORTED", "not saved", "preserve");
        assertThat(AuthoringCompletionContract.validate(checkpoint(checked, write("corrected")), mapper)).isNull();
    }
    @Test void incompleteCandidatesAndSavedCoverageExposeTheActualPendingMappings() {
        var coverage = Map.of("required", true, "complete", false, "completed", 4, "total", 5, "items", List.of(Map.of(
            "state", "PENDING", "operationId", "operation-five", "label", "POST /v1/requests", "savedCandidates", List.of(Map.of(
                "actionName", "create_request", "missingRequiredMappings", List.of(Map.of("location", "body", "name", "productId")))))));
        var checked = step("check", "authoring_check_candidate", false, Map.of(
            "draftSaveRequested", true, "hasChanges", true, "requiresCorrection", false, "sourceCoverage", coverage));
        var finding = AuthoringCompletionContract.validate(checkpoint(checked), mapper);
        assertThat(finding.code()).isEqualTo("WORKFLOW_CHECKED_CANDIDATE_NOT_SAVED");
        assertThat(finding.detail()).contains("POST /v1/requests", "create_request", "productId", "authoring_update_draft");
        var status = mapper.createObjectNode().set("sourceCoverage", mapper.valueToTree(coverage));
        assertThat(AuthoringCompletionContract.coverage(status, false).detail())
            .contains("POST /v1/requests", "productId", "CURRENT_SAVED_CONFIGURATION");
    }
    @Test void advisoryBaselineFindingsDoNotReplaceASaveRequirementAndFeedbackIsBounded() {
        var issues = java.util.stream.IntStream.range(0, 100).mapToObj(i -> Map.of("severity", "ERROR",
            "code", "ISSUE_" + i, "message", "x".repeat(10000))).toList();
        var checked = step("check", "authoring_check_candidate", false, Map.of(
            "draftSaveRequested", true, "hasChanges", true, "requiresCorrection", false,
            "findings", Map.of("valid", false, "errorCount", 100, "issues", issues)));
        assertThat(AuthoringCompletionContract.validate(checkpoint(checked), mapper).code())
            .isEqualTo("WORKFLOW_CHECKED_CANDIDATE_NOT_SAVED");
        var rejected = step("check", "authoring_check_candidate", false, Map.of(
            "draftSaveRequested", true, "hasChanges", true, "requiresCorrection", true,
            "findings", Map.of("valid", false, "errorCount", 100, "issues", issues)));
        assertThat(AuthoringCompletionContract.validate(checkpoint(rejected), mapper).detail())
            .hasSizeLessThanOrEqualTo(2000).contains("truncated", "UNSUPPORTED");
    }
    @Test void previewUsesCheckedCandidateCoverageAndNeverRequiresSavingIt() {
        var status = mapper.createObjectNode();
        status.putObject("sourceCoverage").put("required", true).put("complete", false);
        status.putObject("lastCandidateCoverage").put("evidence", "CANDIDATE_NOT_SAVED").put("complete", true);
        assertThat(AuthoringCompletionContract.coverage(status, true)).isNull();
        assertThat(AuthoringCompletionContract.coverage(status, false).code()).isEqualTo("AUTHORING_SOURCE_COVERAGE_INCOMPLETE");
        status.withObject("/lastCandidateCoverage").put("complete", false);
        assertThat(AuthoringCompletionContract.coverage(status, true).code()).isEqualTo("AUTHORING_PREVIEW_COVERAGE_INCOMPLETE");
    }
    @Test void currentCompleteNoOpAndNonRequiredCoverageDoNotForceAnotherCheckOrWrite() {
        var status = mapper.createObjectNode();
        status.putObject("sourceCoverage").put("required", true).put("complete", true);
        assertThat(AuthoringCompletionContract.coverage(status, false)).isNull();
        assertThat(AuthoringCompletionContract.coverage(status, true)).isNull();
        status.withObject("/sourceCoverage").put("required", false).put("complete", false);
        assertThat(AuthoringCompletionContract.coverage(status, true)).isNull();
    }
}
