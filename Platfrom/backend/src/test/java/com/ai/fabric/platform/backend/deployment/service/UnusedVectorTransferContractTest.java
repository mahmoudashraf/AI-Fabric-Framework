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
        assertThat(r.consumerDetachments()).isEmpty();
    }

    @Test void absentOrEmptyDetachmentApprovalPreservesTheStoredRequestFingerprint() {
        var legacy = json.read(release(), com.fasterxml.jackson.databind.JsonNode.class);
        var request = json.read(release(), ReleaseRequest.class);
        assertThat(json.hash(request)).isEqualTo(json.hash(legacy));
        assertThat(json.write(request)).doesNotContain("consumerDetachments");
        assertThat(json.hash(json.read(withDetachments("[]"), ReleaseRequest.class))).isEqualTo(json.hash(legacy));
    }

    @Test void historicalReceiptWithoutDetachmentsKeepsItsExactFingerprintAndCanBeRead() {
        var request = json.read(release(), ReleaseRequest.class);
        var receipt = new ReleaseReceipt("receipt", "release", "a".repeat(64), request.destination(), request.destination(),
            new VectorIdentity("cluster", "project", "region", "Free", "synthetic.zilliz.com", "default", "COOLIFY"),
            java.util.List.of(), java.util.List.of(), null, null, "approval", "staff", java.time.Instant.EPOCH, null);
        var sealed = json.seal(receipt, ReleaseReceipt.class);
        String stored = json.write(sealed);
        assertThat(stored).doesNotContain("consumerDetachments");
        var restored = json.read(stored, ReleaseReceipt.class);
        assertThat(restored.consumerDetachments()).isEmpty();
        assertThat(json.receiptHash(restored)).isEqualTo(sealed.fingerprint());
        assertThat(json.write(restored)).isEqualTo(stored);
    }

    @Test void explicitConsumerAndDraftExpectationsAreImmutableAndPartOfTheCommandFingerprint() {
        String body = withDetachments("""
            [{"consumerEntityId":"consumer","expectedBindingRevision":1,"draftWorkspaces":[
                {"installationEntityId":"draft","expectedRowVersion":0}]}]
            """);
        var request = json.read(body, ReleaseRequest.class);
        assertThat(request.consumerDetachments()).singleElement().satisfies(c -> {
            assertThat(c.consumerEntityId()).isEqualTo("consumer");
            assertThat(c.expectedBindingRevision()).isEqualTo(1);
            assertThat(c.draftWorkspaces()).singleElement().satisfies(app -> {
                assertThat(app.installationEntityId()).isEqualTo("draft");
                assertThat(app.expectedRowVersion()).isZero();
            });
        });
        assertThatThrownBy(() -> request.consumerDetachments().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> request.consumerDetachments().getFirst().draftWorkspaces().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThat(json.hash(request)).isNotEqualTo(json.hash(json.read(release(), ReleaseRequest.class)));
        assertThat(json.hash(request)).isNotEqualTo(json.hash(json.read(body.replace("\"expectedRowVersion\":0", "\"expectedRowVersion\":1"), ReleaseRequest.class)));
        assertThat(json.read(json.write(request), ReleaseRequest.class)).isEqualTo(request);
    }

    @ParameterizedTest @ValueSource(strings = {"missing-consumer-revision", "null-consumer-revision", "missing-app-revision",
        "null-app-revision", "string-revision", "fractional-revision", "unknown-consumer-field", "unknown-app-field", "duplicate-consumer-field", "duplicate-app-field"})
    void detachmentJsonNeverDefaultsMissingRevisionsOrAcceptsCoercion(String change) {
        String expected = """
            [{"consumerEntityId":"consumer","expectedBindingRevision":1,"draftWorkspaces":[
                {"installationEntityId":"draft","expectedRowVersion":0}]}]
            """;
        String invalid = switch (change) {
            case "missing-consumer-revision" -> expected.replace("\"expectedBindingRevision\":1,", "");
            case "null-consumer-revision" -> expected.replace("\"expectedBindingRevision\":1", "\"expectedBindingRevision\":null");
            case "missing-app-revision" -> expected.replace(",\"expectedRowVersion\":0", "");
            case "null-app-revision" -> expected.replace("\"expectedRowVersion\":0", "\"expectedRowVersion\":null");
            case "string-revision" -> expected.replace("\"expectedBindingRevision\":1", "\"expectedBindingRevision\":\"1\"");
            case "fractional-revision" -> expected.replace("\"expectedRowVersion\":0", "\"expectedRowVersion\":0.5");
            case "unknown-consumer-field" -> expected.replace("\"consumerEntityId\":", "\"trusted\":true,\"consumerEntityId\":");
            case "unknown-app-field" -> expected.replace("\"installationEntityId\":", "\"preserve\":true,\"installationEntityId\":");
            case "duplicate-consumer-field" -> expected.replace("\"expectedBindingRevision\":1", "\"expectedBindingRevision\":2,\"expectedBindingRevision\":1");
            case "duplicate-app-field" -> expected.replace("\"expectedRowVersion\":0", "\"expectedRowVersion\":1,\"expectedRowVersion\":0");
            default -> throw new AssertionError(change);
        };
        assertThatThrownBy(() -> json.read(withDetachments(invalid), ReleaseRequest.class)).hasMessage("UNUSED_VECTOR_TRANSFER_JSON_INVALID");
    }

    private String withDetachments(String detachments) {
        String body = release();
        return body.substring(0, body.length() - 1) + ",\"consumerDetachments\":" + detachments + "}";
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
