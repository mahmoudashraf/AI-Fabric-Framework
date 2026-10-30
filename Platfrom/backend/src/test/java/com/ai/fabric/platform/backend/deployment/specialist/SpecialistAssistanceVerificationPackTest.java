package com.ai.fabric.platform.backend.deployment.specialist;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SpecialistAssistanceVerificationPackTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SpecialistAssistanceVerificationPack pack = new SpecialistAssistanceVerificationPack(mapper);

    @Test void qualifiesOnlyTheExactPublishedBundleAndReadyRegistryCatalogue() {
        ObjectNode behavior = published();
        var qualification = pack.qualify(behavior, overview(behavior));
        assertThat(qualification.passed()).isTrue();
        assertThat(qualification.details().path("verificationPackId").asText()).isEqualTo(SpecialistAssistanceVerificationPack.ID);
        assertThat(qualification.details().toString()).doesNotContain("instructions", "resources", "requiredScopes");
    }

    @ParameterizedTest
    @ValueSource(strings = {"hash", "not-ready", "disabled", "contract", "missing-overview", "capability", "extension", "pack", "invocation", "scope", "extra-invocation", "invalid-published-hash"})
    void doesNotAcceptStaleOrUnqualifiedReadback(String fault) {
        ObjectNode behavior = published();
        ObjectNode overview = overview(behavior);
        ObjectNode status = (ObjectNode) overview.path("specialistAssistance");
        ObjectNode manifest = (ObjectNode) overview.path("runtimeCapabilityManifest");
        switch (fault) {
            case "hash" -> status.put("contentHash", "sha256:" + "0".repeat(64));
            case "not-ready" -> status.put("ready", false);
            case "disabled" -> status.put("enabled", false);
            case "contract" -> status.put("contractVersion", "unrecognized");
            case "missing-overview" -> overview.removeAll();
            case "capability" -> manifest.putArray("capabilities");
            case "extension" -> manifest.putArray("supportedExecutionExtensions");
            case "pack" -> manifest.putArray("verificationPackIds");
            case "invocation" -> ((ObjectNode) status.path("invocations").get(0)).put("targetRef", "another-specialist@1");
            case "scope" -> ((ObjectNode) status.path("invocations").get(0)).putArray("requiredScopes").add("unapproved:read");
            case "extra-invocation" -> status.withArray("invocations").add(status.path("invocations").get(0).deepCopy());
            case "invalid-published-hash" -> ((ObjectNode) behavior.path("specialistAssistance")).put("contentHash", "invalid");
            default -> throw new AssertionError(fault);
        }
        assertThat(pack.qualify(behavior, overview).passed()).as(fault).isFalse();
    }

    private ObjectNode published() {
        return new SpecialistAssistanceCompiler(mapper).compileBehavior(
            SpecialistAssistanceFixtures.behavior(mapper), SpecialistAssistanceFixtures.authority());
    }

    private ObjectNode overview(ObjectNode behavior) {
        ObjectNode result = mapper.createObjectNode().put("success", true);
        var status = result.putObject("specialistAssistance");
        status.put("enabled", true).put("ready", true).put("contractVersion", SpecialistAssistanceCompiler.CONTRACT)
            .put("contentHash", behavior.path("specialistAssistance").path("contentHash").asText());
        status.set("invocations", behavior.at("/specialistAssistance/compiled/invocations").deepCopy());
        var manifest = result.putObject("runtimeCapabilityManifest");
        manifest.putArray("capabilities").add(SpecialistAssistanceCompiler.CAPABILITY);
        manifest.putArray("supportedExecutionExtensions").add(SpecialistAssistanceCompiler.EXTENSION);
        manifest.putArray("verificationPackIds").add(SpecialistAssistanceVerificationPack.ID);
        return result;
    }
}
