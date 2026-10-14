package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.TreeMap;

final class DeploymentEvidenceFingerprint {
    private DeploymentEvidenceFingerprint() { }

    static String hash(ObjectMapper mapper, JsonNode node) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(canonical(node))));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot fingerprint deployment evidence.", error);
        }
    }

    private static Object canonical(JsonNode node) {
        if (node.isObject()) {
            var sorted = new TreeMap<String, Object>();
            node.fields().forEachRemaining(field -> sorted.put(field.getKey(), canonical(field.getValue())));
            return sorted;
        }
        if (node.isArray()) {
            var values = new ArrayList<Object>();
            node.forEach(value -> values.add(canonical(value)));
            return values;
        }
        return node;
    }
}
