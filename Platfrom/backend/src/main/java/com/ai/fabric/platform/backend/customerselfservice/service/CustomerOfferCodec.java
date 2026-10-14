package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;

@Component
public class CustomerOfferCodec {
    private final ObjectMapper mapper;

    public CustomerOfferCodec(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public String write(Object value) {
        try { return mapper.writeValueAsString(canonical(mapper.valueToTree(value))); }
        catch (Exception ex) { throw CustomerSelfServiceException.invalid(); }
    }

    public <T> T read(String json, Class<T> type) {
        try {
            T value = mapper.readValue(json, type);
            if (value == null) throw CustomerSelfServiceException.invalid();
            return value;
        }
        catch (Exception ex) { throw CustomerSelfServiceException.invalid(); }
    }

    public JsonNode tree(String json) {
        try {
            JsonNode node = mapper.readTree(json);
            if (node == null || !node.isObject()) throw CustomerSelfServiceException.invalid();
            return node;
        } catch (Exception ex) { throw CustomerSelfServiceException.invalid(); }
    }

    public String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(write(value).getBytes(StandardCharsets.UTF_8)));
        } catch (CustomerSelfServiceException ex) { throw ex; }
        catch (Exception ex) { throw new IllegalStateException("SHA-256 is unavailable.", ex); }
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            TreeMap<String, JsonNode> fields = new TreeMap<>();
            node.fields().forEachRemaining(entry -> fields.put(entry.getKey(), canonical(entry.getValue())));
            ObjectNode result = mapper.createObjectNode();
            fields.forEach(result::set);
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        return node;
    }
}
