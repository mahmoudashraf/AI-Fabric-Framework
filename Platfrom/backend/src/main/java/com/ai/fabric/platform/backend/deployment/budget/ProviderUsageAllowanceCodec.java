package com.ai.fabric.platform.backend.deployment.budget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.TreeMap;

final class ProviderUsageAllowanceCodec {
    private final ObjectMapper mapper;
    ProviderUsageAllowanceCodec(ObjectMapper mapper) { this.mapper = mapper; }
    String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception error) { throw ProviderUsageAllowanceFailure.conflict("PROVIDER_ALLOWANCE_RECORD_INVALID"); }
    }
    <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (Exception error) { throw ProviderUsageAllowanceFailure.conflict("PROVIDER_ALLOWANCE_RECORD_INVALID"); }
    }
    String fingerprint(Object value) { return sha256(write(canonical(mapper.valueToTree(value)))); }
    static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception error) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    private Object canonical(JsonNode node) {
        if (node.isObject()) {
            var result = new TreeMap<String, Object>();
            node.fields().forEachRemaining(field -> result.put(field.getKey(), canonical(field.getValue())));
            return result;
        }
        if (node.isArray()) {
            var result = new ArrayList<Object>(); node.forEach(value -> result.add(canonical(value))); return result;
        }
        return node;
    }
}
