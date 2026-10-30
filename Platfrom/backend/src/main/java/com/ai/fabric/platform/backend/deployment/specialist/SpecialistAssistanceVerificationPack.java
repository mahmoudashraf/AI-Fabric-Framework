package com.ai.fabric.platform.backend.deployment.specialist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Set;

/** Exact published-resource qualification, not a semantic judge or a second execution engine. */
public final class SpecialistAssistanceVerificationPack {
    public static final String ID = "conversational-specialist-assistance-v1";
    public static final String RELEASE_CHECK = "runtime_specialist_assistance_matches_expected";
    public static final Set<String> REQUIRED_CHECKS = Set.of(
        "AUTHENTICATED_QUERY", "CONTINUATION", "STRUCTURED_RESULT",
        "COMPILED_BUNDLE_HASH", "SPECIALIST_CAPABILITY_READY", "EXACT_INVOCATION_CATALOGUE"
    );
    private final ObjectMapper mapper;

    public SpecialistAssistanceVerificationPack(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public Qualification qualify(JsonNode behavior, JsonNode overview) {
        JsonNode config = behavior.path(SpecialistAssistanceCompiler.FIELD);
        JsonNode compiled = config.path("compiled");
        String expectedHash = config.path("contentHash").asText("");
        boolean publishedBundleValid;
        try {
            new SpecialistAssistanceCompiler(mapper).verifyCompiled(compiled, expectedHash);
            publishedBundleValid = true;
        } catch (RuntimeException invalid) {
            publishedBundleValid = false;
        }
        JsonNode actual = overview.path(SpecialistAssistanceCompiler.FIELD);
        JsonNode manifest = overview.path("runtimeCapabilityManifest");
        boolean hashMatches = publishedBundleValid && expectedHash.equals(actual.path("contentHash").asText(""));
        boolean catalogueMatches = compiled.path("invocations").isArray()
            && !compiled.path("invocations").isEmpty()
            && compiled.path("invocations").equals(actual.path("invocations"));
        boolean capabilityReady = overview.path("success").asBoolean(false)
            && actual.path("enabled").asBoolean(false) && actual.path("ready").asBoolean(false)
            && SpecialistAssistanceCompiler.CONTRACT.equals(actual.path("contractVersion").asText(""))
            && contains(manifest.path("capabilities"), SpecialistAssistanceCompiler.CAPABILITY)
            && contains(manifest.path("supportedExecutionExtensions"), SpecialistAssistanceCompiler.EXTENSION)
            && contains(manifest.path("verificationPackIds"), ID);
        ObjectNode details = mapper.createObjectNode();
        details.put("verificationPackId", ID);
        details.put("expectedContentHash", expectedHash);
        details.put("actualContentHash", actual.path("contentHash").asText(""));
        details.put("publishedBundleValid", publishedBundleValid);
        details.put("compiledBundleHashMatches", hashMatches);
        details.put("specialistCapabilityReady", capabilityReady);
        details.put("exactInvocationCatalogue", catalogueMatches);
        details.put("expectedInvocationCount", compiled.path("invocations").size());
        details.put("actualInvocationCount", actual.path("invocations").size());
        return new Qualification(publishedBundleValid && hashMatches && capabilityReady && catalogueMatches, details);
    }

    private boolean contains(JsonNode values, String expected) {
        for (JsonNode value : values) if (expected.equals(value.asText())) return true;
        return false;
    }

    public record Qualification(boolean passed, ObjectNode details) {}
}
