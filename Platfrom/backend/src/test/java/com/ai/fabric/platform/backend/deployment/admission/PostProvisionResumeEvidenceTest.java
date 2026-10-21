package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostProvisionResumeEvidenceTest extends AdmissionFixture {
    private JdbcTemplate business;
    private Reservation current;
    private PostProvisionResumeContracts.Receipt receipt;
    private ObjectNode failedRelease;
    private Map<String, Object> verification;
    private final Map<String, String> inventory = new TreeMap<>();
    private final List<PostProvisionResumeContracts.Candidate> checkpoints = new ArrayList<>();

    @BeforeEach void continuation() {
        var prior = one(); var value = (ObjectNode) mapper.valueToTree(prior);
        value.put("revision", prior.revision() + 2); current = json.read(json.write(value), Reservation.class);
        var nativeDetails = Map.of("progress", Map.of("steps", List.of(Map.of("key", "original-native-proof", "status", "COMPLETED"))));
        var candidate = new PostProvisionResumeContracts.Candidate(prior, Map.of("graph", "g", "handles", "h", "dependencyOwnership", "o"),
            json.write(Map.of("status", "FAILED", "provisioning_details_json", json.write(nativeDetails))), NOW, NOW, List.of("retained-allocation"));
        var command = new PostProvisionResumeContracts.Command(prior.fence(), "failed-release", "previous-resume", "Continue setup", true, "a".repeat(64));
        var summary = new PostProvisionResumeContracts.Summary("receipt", prior.id(), prior.generation(), prior.revision(),
            prior.revision() + 1, "failed-release", "APPLY_REQUESTED", false, false, candidate.retainedAllocationIds(), NOW);
        receipt = new PostProvisionResumeContracts.Receipt("owner", "b".repeat(64), command, candidate,
            new FailedApplyRetryContracts.NativeReadback(NOW, Map.of()), summary);
        var details = mapper.createObjectNode();
        details.putObject("postProvisionResume").put("boundary", PostProvisionResumeContracts.BOUNDARY)
            .put("operationKey", command.operationKey()).put("proofFingerprint", command.proofFingerprint()).put("failedAt", NOW.toString());
        var steps = details.putObject("progress").putArray("steps");
        steps.addObject().put("key", "queue_release").put("status", "QUEUED");
        steps.addObject().put("key", "preflight_verification").put("status", "FAILED");
        failedRelease = mapper.createObjectNode().put("id", "failed-release").put("status", "PRE_APPLY_BLOCKED")
            .put("provisioning_status", "BLOCKED").put("verification_status", "FAILED")
            .put("current_step_key", "preflight_verification").put("verification_run_id", "failed-preflight")
            .put("provisioning_details_json", json.write(details));
        verification = new TreeMap<>(Map.of("id", "failed-preflight", "deployment_id", prior.deploymentId(),
            "release_id", "failed-release", "deployment_version_id", "version", "verification_type", "PRE_APPLY", "status", "FAILED"));
        business = mock(JdbcTemplate.class);
        when(business.queryForList(anyString(), eq("failed-preflight"))).thenAnswer(call -> List.of(verification));
    }

    @Test void preflightOnlyFailureFollowsDurableReceiptAndCollectsVerificationFence() {
        var nativeDetails = nativePhase();
        assertThat(nativeDetails.path("progress").path("steps").get(0).path("key").asText()).isEqualTo("original-native-proof");
        assertThat(checkpoints).containsExactly(receipt.failedAttempt());
        assertThat(inventory).containsEntry("preflightVerification:failed-preflight", json.fingerprint(verification));
    }

    @ParameterizedTest @ValueSource(strings = {"release_id", "deployment_id", "deployment_version_id", "verification_type", "status"})
    void rejectsForeignOrNonFailedPreApplyVerification(String field) {
        verification.put(field, "foreign");
        assertThatThrownBy(this::nativePhase).isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("POST_PROVISION_RESUME_PREFLIGHT_VERIFICATION_UNPROVEN");
    }

    @ParameterizedTest @ValueSource(strings = {"queue-status", "preflight-status", "later-step", "receipt-proof", "missing-receipt"})
    void rejectsProgressOrReceiptThatCannotProveNoLaterEffects(String change) {
        var details = (ObjectNode) json.read(failedRelease.path("provisioning_details_json").asText(), JsonNode.class);
        var steps = (com.fasterxml.jackson.databind.node.ArrayNode) details.path("progress").path("steps");
        switch (change) {
            case "queue-status" -> ((ObjectNode) steps.get(0)).put("status", "COMPLETED");
            case "preflight-status" -> ((ObjectNode) steps.get(1)).put("status", "COMPLETED");
            case "later-step" -> steps.addObject().put("key", "resume_managed_dependencies").put("status", "RUNNING");
            case "receipt-proof" -> ((ObjectNode) details.path("postProvisionResume")).put("proofFingerprint", "0".repeat(64));
            case "missing-receipt" -> receipt = null;
            default -> throw new AssertionError(change);
        }
        failedRelease.put("provisioning_details_json", json.write(details));
        assertThatThrownBy(this::nativePhase).isInstanceOf(AdmissionFailure.class)
            .hasMessageContaining("POST_PROVISION_RESUME_CONTROL_FLOW_UNPROVEN");
    }

    private JsonNode nativePhase() {
        var evidence = new PostProvisionResumeEvidence(business, json, key -> receipt);
        return ReflectionTestUtils.invokeMethod(evidence, "nativePhase", failedRelease, current, "failed-release",
            manifest("manifest1", "deployment1"), inventory, checkpoints);
    }
}
