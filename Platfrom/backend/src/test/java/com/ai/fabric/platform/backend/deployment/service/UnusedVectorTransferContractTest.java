package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.UnusedVectorTransferContracts.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class UnusedVectorTransferContractTest {
    private final UnusedVectorTransferJson json = new UnusedVectorTransferJson();
    private static final String SCOPE = """
        {"controlPlane":{"id":"destination","origin":"https://destination.example.test"},"customerId":"customer",
         "tenantId":"tenant","deploymentId":"deployment","targetProfileId":"target","versionId":"version",
         "versionConfigHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","collectionPrefix":"new_"}
        """;
    private String release() { return "{\"operationKey\":\"release\",\"approvalReference\":\"approval\",\"bindingId\":\"binding\",\"destination\":" + SCOPE
        + ",\"sources\":[{\"bindingId\":\"binding\",\"expectedBindingFingerprint\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"cancellationReceiptId\":null}],"
        + "\"inventoryApproval\":{\"expectedCredentialFingerprint\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"fullVisibilityReviewReference\":\"review\",\"noWriterWindowReference\":\"window\"}}"; }

    @Test void exactReleaseIsParsedWithoutInventingACancellation() {
        var r = json.read(release(), ReleaseRequest.class);
        assertThat(r.sources()).singleElement().satisfies(s -> assertThat(s.cancellationReceiptId()).isNull());
        assertThat(r.destination().controlPlane().id()).isEqualTo("destination");
    }
    @ParameterizedTest @ValueSource(strings = {"unknown", "duplicate", "nestedDuplicate", "case", "scalar", "nestedScalar", "array", "trailing"})
    void releaseParsingIsStrictAtEveryStructuredLevel(String change) {
        String body = release();
        body = switch (change) {
            case "unknown" -> body.replace("\"operationKey\":", "\"reviewed\":true,\"operationKey\":");
            case "duplicate" -> body.replace("\"operationKey\":", "\"operationKey\":\"different\",\"operationKey\":");
            case "nestedDuplicate" -> body.replace("\"id\":\"destination\"", "\"id\":\"other\",\"id\":\"destination\"");
            case "case" -> body.replace("\"operationKey\"", "\"OperationKey\"");
            case "scalar" -> body.replace("\"operationKey\":\"release\"", "\"operationKey\":1");
            case "nestedScalar" -> body.replace("\"origin\":\"https://destination.example.test\"", "\"origin\":true");
            case "array" -> "[" + body + "]";
            case "trailing" -> body + " {}";
            default -> throw new AssertionError();
        };
        String invalid = body;
        assertThatThrownBy(() -> json.read(invalid, ReleaseRequest.class)).hasMessage("UNUSED_VECTOR_TRANSFER_JSON_INVALID");
    }
    @ParameterizedTest @ValueSource(strings = {"TOKEN", "token", "1", "true"})
    void claimAuthenticationDoesNotCoerceOrFoldCase(String mode) {
        String encoded = mode.equals("1") || mode.equals("true") ? mode : "\"" + mode + "\"";
        String body = "{\"operationKey\":\"claim\",\"reviewId\":\"review\",\"connection\":{\"authType\":" + encoded + "}}";
        if (mode.equals("TOKEN")) assertThat(json.read(body, ClaimRequest.class).connection().authType().name()).isEqualTo("TOKEN");
        else assertThatThrownBy(() -> json.read(body, ClaimRequest.class)).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"https://local.example.test/", "http://local.example.test", "https://local.example.test:443", "https://user@local.example.test", "https://LOCAL.example.test", "https://local.example.test?q=x"})
    void localIdentityRejectsNoncanonicalOrigins(String origin) {
        var identity = new UnusedVectorTransferIdentity("local", origin, "{}", json);
        assertThatThrownBy(identity::local).hasMessageContaining("CP_IDENTITY_REQUIRED");
    }
    @Test void callerCannotChooseLocalOrUnconfiguredPeerIdentity() {
        var identity = new UnusedVectorTransferIdentity("local", "https://local.example.test", "{\"peer\":\"https://peer.example.test\"}", json);
        assertThatThrownBy(() -> identity.requireLocal(new ControlPlane("peer", "https://peer.example.test"))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> identity.requirePeer(new ControlPlane("peer", "https://evil.example.test"))).isInstanceOf(RuntimeException.class);
        identity.requirePeer(new ControlPlane("peer", "https://peer.example.test"));
    }
}
