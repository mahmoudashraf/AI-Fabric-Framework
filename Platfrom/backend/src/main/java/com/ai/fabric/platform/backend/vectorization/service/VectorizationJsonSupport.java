package com.ai.fabric.platform.backend.vectorization.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class VectorizationJsonSupport {

    private final ObjectMapper objectMapper;

    public VectorizationJsonSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ObjectNode objectNode() {
        return objectMapper.createObjectNode();
    }

    public ArrayNode arrayNode() {
        return objectMapper.createArrayNode();
    }

    public JsonNode readTree(String value) {
        try {
            return value == null || value.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse stored vectorization JSON.", ex);
        }
    }

    public ObjectNode readObject(String value) {
        JsonNode node = readTree(value);
        return node instanceof ObjectNode objectNode ? objectNode : objectNode();
    }

    public String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value == null ? objectMapper.createObjectNode() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize vectorization JSON.", ex);
        }
    }

    public String writeList(List<String> values) {
        ArrayNode arrayNode = objectMapper.createArrayNode();
        if (values != null) {
            values.stream().filter(value -> value != null && !value.isBlank()).forEach(arrayNode::add);
        }
        return write(arrayNode);
    }

    public List<String> readStringList(String value) {
        JsonNode node = readTree(value);
        List<String> out = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> {
                String text = item.asText("").trim();
                if (!text.isBlank()) {
                    out.add(text);
                }
            });
        }
        return out;
    }
}
