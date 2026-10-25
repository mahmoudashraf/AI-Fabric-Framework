package com.ai.fabric.platform.backend.deployment.operations.lifecycle.nativeadapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.TreeMap;

final class NativeLifecycleFingerprint {
    private final ObjectMapper mapper;
    NativeLifecycleFingerprint(ObjectMapper mapper) { this.mapper = mapper; }

    String hash(Object value) {
        try {
            byte[] json = mapper.writeValueAsString(canonical(mapper.valueToTree(value))).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (Exception error) {
            throw new IllegalStateException("Native lifecycle identity cannot be serialized.");
        }
    }

    private Object canonical(JsonNode node) {
        if (node.isObject()) {
            var result = new TreeMap<String, Object>();
            node.fields().forEachRemaining(entry -> result.put(entry.getKey(), canonical(entry.getValue())));
            return result;
        }
        if (node.isArray()) {
            var result = new ArrayList<Object>();
            node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        return node;
    }
}
