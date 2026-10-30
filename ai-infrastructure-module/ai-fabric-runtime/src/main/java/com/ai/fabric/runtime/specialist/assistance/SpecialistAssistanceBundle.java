package com.ai.fabric.runtime.specialist.assistance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Immutable server-delivered resources, not a customer URL or runtime editor. */
@Component
public final class SpecialistAssistanceBundle {
    public static final String CONTRACT = "LOOMAI_SPECIALIST_ASSISTANCE_V1";
    private static final int MAX_BYTES = 65_536;
    private final JsonNode content;
    private final String contentHash;
    private final List<Map<String, Object>> invocations;

    public SpecialistAssistanceBundle(ObjectMapper mapper,
        @Value("${LOOMAI_SPECIALIST_ASSISTANCE_BUNDLE_BASE64:}") String encoded,
        @Value("${LOOMAI_SPECIALIST_ASSISTANCE_BUNDLE_HASH:}") String expectedHash) {
        if (encoded == null || encoded.isBlank()) {
            if (expectedHash != null && !expectedHash.isBlank()) throw new IllegalStateException("Specialist bundle content is missing.");
            content = mapper.createObjectNode();
            contentHash = null;
            invocations = List.of();
            return;
        }
        try {
            if (encoded.length() > 4 * ((MAX_BYTES + 2) / 3)) throw new IllegalArgumentException();
            byte[] bytes = Base64.getDecoder().decode(encoded);
            String hash = "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if (bytes.length > MAX_BYTES || !hash.equals(expectedHash)) throw new IllegalArgumentException();
            JsonNode parsed = mapper.readTree(bytes);
            if (!CONTRACT.equals(parsed.path("contractVersion").asText())
                || !parsed.path("resources").isArray() || parsed.path("resources").isEmpty()
                || !parsed.path("invocations").isArray() || parsed.path("invocations").isEmpty()
                || parsed.path("resources").size() > 64 || parsed.path("invocations").size() > 16) throw new IllegalArgumentException();
            var ids = new HashSet<String>();
            for (JsonNode invocation : parsed.path("invocations")) {
                if (!invocation.path("id").asText().matches("[a-z][a-z0-9-]{1,63}")
                    || !ids.add(invocation.path("id").asText())
                    || !List.of("SPECIALIST", "CHAIN").contains(invocation.path("targetType").asText())
                    || !invocation.path("targetRef").asText().matches("[a-z][a-z0-9-]{1,79}@[A-Za-z0-9][A-Za-z0-9._-]{0,39}")
                    || !invocation.path("requiredScopes").isArray()) throw new IllegalArgumentException();
            }
            for (JsonNode resource : parsed.path("resources")) {
                if (!List.of("Specialist", "SpecialistChain", "SpecialistSchema", "SpecialistPromptProfile")
                    .contains(resource.path("kind").asText())) throw new IllegalArgumentException();
            }
            content = parsed;
            contentHash = hash;
            invocations = mapper.convertValue(parsed.path("invocations"), new TypeReference<>() {});
        } catch (Exception invalid) {
            throw new IllegalStateException("Specialist assistance requires a bounded hash-verified published bundle.", invalid);
        }
    }

    public boolean enabled() { return contentHash != null; }
    public JsonNode content() { return content.deepCopy(); }
    public String contentHash() { return contentHash; }
    public List<Map<String, Object>> invocationCatalogue() { return invocations; }
}
