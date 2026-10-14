package com.ai.fabric.platform.backend.deployment.retirement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

@Component
public class TargetRetirementJson {
    private final ObjectMapper mapper;
    public TargetRetirementJson(ObjectMapper mapper) { this.mapper = mapper; }
    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception ex) { throw new IllegalStateException("Cannot persist retirement evidence.", ex); }
    }
    public <T> T read(String value, Class<T> type) {
        try { return mapper.readValue(value, type); }
        catch (Exception ex) { throw new IllegalStateException("Invalid persisted retirement evidence.", ex); }
    }
    public String fingerprint(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(write(canonical(mapper.valueToTree(value))).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new IllegalStateException("Cannot fingerprint retirement evidence.", ex); }
    }
    private Object canonical(JsonNode value) {
        if (value.isObject()) {
            var sorted = new TreeMap<String, Object>();
            value.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), canonical(entry.getValue())));
            return sorted;
        }
        if (value.isArray()) {
            var list = new java.util.ArrayList<Object>();
            value.forEach(item -> list.add(canonical(item)));
            return list;
        }
        return value;
    }
}
