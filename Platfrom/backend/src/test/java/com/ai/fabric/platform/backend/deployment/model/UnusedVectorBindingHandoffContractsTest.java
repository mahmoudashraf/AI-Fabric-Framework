package com.ai.fabric.platform.backend.deployment.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.ai.fabric.platform.backend.deployment.model.UnusedVectorBindingHandoffContracts.*;
import static org.assertj.core.api.Assertions.*;

class UnusedVectorBindingHandoffContractsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String SOURCE = "{\"bindingId\":\"binding\",\"cancellationReceiptId\":\"cancel\",\"expectedBindingFingerprint\":\"hash\"}";
    private static final String CONNECTION = """
        {"versionId":"v","clusterId":"c","projectId":"p","regionId":"r","expectedDeploymentOption":"Free",
        "databaseName":"default","approvalReference":"review","authType":"TOKEN","token":"synthetic"}
        """;

    @Test void historicalCancellationSourceBytesAndReceiptRoundtripAreUnchanged() throws Exception {
        var source = new Source("binding", "cancel", "hash");
        assertThat(mapper.writeValueAsString(source)).isEqualTo(SOURCE);
        assertThat(mapper.readValue(SOURCE, Source.class)).isEqualTo(source);
        var receipt = new Receipt("id", "key", "command", "dep", "customer", "tenant", "target", "cluster", "v", "config",
            java.util.List.of(source), "approval", null, "actor", null, "fingerprint");
        String encoded = mapper.writeValueAsString(receipt);
        assertThat(encoded).doesNotContain("knownNoEffectSettlementReceiptId", "emptyClusterReadback", "sourceTransitions");
        assertThat(mapper.readValue(encoded, Receipt.class)).isEqualTo(receipt);
    }

    @Test void historicalRequestDoesNotAcquireNullInventoryFields() throws Exception {
        var request = mapper.readValue(request(SOURCE, null), Request.class);
        assertThat(mapper.writeValueAsString(request)).doesNotContain("inventoryApproval", "knownNoEffectSettlementReceiptId");
    }

    @Test void explicitSettlementProofAndReviewedInventoryRoundtrip() throws Exception {
        String source = "{\"bindingId\":\"binding\",\"knownNoEffectSettlementReceiptId\":\"settled\",\"expectedBindingFingerprint\":\"hash\"}";
        var value = mapper.readValue(request(source, inventory()), Request.class);
        assertThat(value.sources().getFirst().cancellationReceiptId()).isNull();
        assertThat(value.sources().getFirst().knownNoEffectSettlementReceiptId()).isEqualTo("settled");
        assertThat(mapper.readValue(mapper.writeValueAsString(value), Request.class)).isEqualTo(value);
    }

    @ParameterizedTest @ValueSource(strings = {"both", "neither", "empty", "scalar", "missing-review", "missing-window", "unknown-review", "unneeded-review"})
    void proofKindsAndInventoryCannotBeImplicitOrPartiallySpecified(String change) {
        String source = "{\"bindingId\":\"binding\",\"knownNoEffectSettlementReceiptId\":\"settled\",\"expectedBindingFingerprint\":\"hash\"}";
        String review = inventory();
        switch (change) {
            case "both" -> source = source.replace("\"bindingId\"", "\"cancellationReceiptId\":\"cancel\",\"bindingId\"");
            case "neither" -> source = source.replace("\"knownNoEffectSettlementReceiptId\":\"settled\",", "");
            case "empty" -> source = source.replace("\"settled\"", "\"\"");
            case "scalar" -> source = source.replace("\"settled\"", "1");
            case "missing-review" -> review = null;
            case "missing-window" -> review = "{\"expectedCredentialFingerprint\":\"credential\",\"fullVisibilityReviewReference\":\"visibility\"}";
            case "unknown-review" -> review = review.replace("{", "{\"inferAcl\":true,");
            case "unneeded-review" -> source = SOURCE;
            default -> throw new AssertionError(change);
        }
        String body = request(source, review);
        assertThatThrownBy(() -> mapper.readValue(body, Request.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    private String inventory() { return "{\"expectedCredentialFingerprint\":\"credential\",\"fullVisibilityReviewReference\":\"visibility\",\"noWriterWindowReference\":\"window\"}"; }
    private String request(String source, String inventory) {
        return "{\"operationKey\":\"key\",\"approvalReference\":\"review\",\"sources\":[" + source + "],\"connection\":" + CONNECTION
            + (inventory == null ? "" : ",\"inventoryApproval\":" + inventory) + "}";
    }
}
