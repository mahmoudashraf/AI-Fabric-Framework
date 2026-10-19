package com.ai.fabric.platform.backend.deployment.budget;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageGrantLifecycleContracts.*;
import static org.assertj.core.api.Assertions.*;

class ProviderUsageGrantLifecycleContractsTest {
    private final ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test void unknownFieldsAreRejectedEvenWithPermissiveApplicationMapper() throws Exception {
        ObjectNode body = mapper.valueToTree(command()); body.put("refund", true);
        assertThatThrownBy(() -> mapper.treeToValue(body, VoidUnusedCommand.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        ObjectNode nested = mapper.valueToTree(command()); ((ObjectNode) nested.get("expectedAllocated")).put("balance", 0);
        assertThatThrownBy(() -> mapper.treeToValue(nested, VoidUnusedCommand.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThat(mapper.treeToValue(mapper.valueToTree(command()), VoidUnusedCommand.class)).isEqualTo(command());
    }

    @Test void directCommandsRequireExplicitZerosAndExactBoundedReviewReferences() {
        var original = command();
        assertThatThrownBy(() -> new ZeroAllocatedCredits(null, 0L, 0L)).isInstanceOf(ProviderUsageAllowanceFailure.class);
        assertThatThrownBy(() -> new ZeroAllocatedCredits(0L, 1L, 0L)).isInstanceOf(ProviderUsageAllowanceFailure.class);
        assertThatThrownBy(() -> new ZeroAllocatedCredits(0L, 0L, -1L)).isInstanceOf(ProviderUsageAllowanceFailure.class);
        for (Long count : java.util.Arrays.asList(null, -1L, 1L))
            assertThatThrownBy(() -> new VoidUnusedCommand(original.operationKey(), original.expectedGrantFingerprint(), count,
                original.expectedAllocated(), original.reason(), original.ownerApprovalEvidenceRef())).isInstanceOf(ProviderUsageAllowanceFailure.class);
        assertThatThrownBy(() -> new VoidUnusedCommand(original.operationKey(), original.expectedGrantFingerprint(), 0L,
            null, original.reason(), original.ownerApprovalEvidenceRef())).isInstanceOf(ProviderUsageAllowanceFailure.class);
        for (String key : List.of("", " leading", "trailing ", "line\nbreak", "x".repeat(129)))
            assertThatThrownBy(() -> new VoidUnusedCommand(key, original.expectedGrantFingerprint(), 0L,
                original.expectedAllocated(), original.reason(), original.ownerApprovalEvidenceRef())).isInstanceOf(ProviderUsageAllowanceFailure.class);
        assertThatThrownBy(() -> new VoidUnusedCommand(original.operationKey(), original.expectedGrantFingerprint(), 0L,
            original.expectedAllocated(), "x".repeat(1001), original.ownerApprovalEvidenceRef())).isInstanceOf(ProviderUsageAllowanceFailure.class);
        assertThatThrownBy(() -> new VoidUnusedCommand(original.operationKey(), original.expectedGrantFingerprint(), 0L,
            original.expectedAllocated(), original.reason(), "x".repeat(256))).isInstanceOf(ProviderUsageAllowanceFailure.class);
    }

    @Test void duplicateFieldsAreRejectedWithoutChangingMapperOrParserFeatures() throws Exception {
        String valid = mapper.writeValueAsString(command());
        assertThatThrownBy(() -> mapper.readValue(valid.replace("\"expectedGrantFingerprint\":", "\"expectedGrantFingerprint\":\"" + "b".repeat(64) + "\",\"expectedGrantFingerprint\":"), VoidUnusedCommand.class))
            .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThatThrownBy(() -> mapper.readValue(valid.replace("\"chatAttempts\":0", "\"chatAttempts\":0,\"chatAttempts\":0"), VoidUnusedCommand.class))
            .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThat(mapper.readTree("{\"unrelated\":1,\"unrelated\":2}").get("unrelated").intValue()).isEqualTo(2);
        assertThat(mapper.isEnabled(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY)).isFalse();
    }

    private VoidUnusedCommand command() {
        return new VoidUnusedCommand("reviewed-correction", "a".repeat(64), 0L,
            new ZeroAllocatedCredits(0L, 0L, 0L), "Correct unused approval scope", "private-evidence-ref");
    }
}
