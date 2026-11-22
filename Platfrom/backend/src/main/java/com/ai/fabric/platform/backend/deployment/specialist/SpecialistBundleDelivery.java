package com.ai.fabric.platform.backend.deployment.specialist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/** Keeps provider shell arguments bounded; this is a startup transport contract, not an AI decision. */
public final class SpecialistBundleDelivery {
    public static final String CAPABILITY = "signed-specialist-bundle-bootstrap-v1";
    public static final int MAX_INLINE_BYTES = 16_384;

    private SpecialistBundleDelivery() { }

    public static boolean requiresArtifact(JsonNode behavior, ObjectMapper mapper) {
        return SpecialistAssistanceCompiler.enabled(behavior)
            && new SpecialistAssistanceCompiler(mapper).serialized(behavior.at("/specialistAssistance/compiled"))
                .getBytes(StandardCharsets.UTF_8).length > MAX_INLINE_BYTES;
    }
}
