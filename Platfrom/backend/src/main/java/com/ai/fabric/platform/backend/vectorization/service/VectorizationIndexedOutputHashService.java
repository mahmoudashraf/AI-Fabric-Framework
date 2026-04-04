package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class VectorizationIndexedOutputHashService {

    private final ObjectMapper objectMapper;

    public VectorizationIndexedOutputHashService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String compute(DeploymentVersionEntity version) {
        if (version == null) {
            return null;
        }
        try {
            JsonNode entityConfig = objectMapper.readTree(version.getEntityConfigJson());
            JsonNode providerConfig = objectMapper.readTree(version.getProviderConfigJson());

            ObjectNode canonical = objectMapper.createObjectNode();
            canonical.set("entities", canonicalEntities(entityConfig.path("ai-entities")));
            canonical.set("aiConfig", entityConfig.path("ai-config").deepCopy());
            canonical.set("providerConfig", providerConfig.deepCopy());
            return sha256(objectMapper.writeValueAsString(canonical));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute indexed output hash.", ex);
        }
    }

    private ArrayNode canonicalEntities(JsonNode node) {
        ArrayNode entities = objectMapper.createArrayNode();
        if (!node.isObject()) {
            return entities;
        }
        List<String> entityNames = new ArrayList<>();
        node.fieldNames().forEachRemaining(entityNames::add);
        entityNames.stream().sorted().forEach(entityName -> {
            JsonNode entityNode = node.path(entityName);
            ObjectNode normalized = objectMapper.createObjectNode();
            normalized.put("entityType", entityName);
            normalized.set("searchableFields", canonicalNamedFields(entityNode.path("searchable-fields")));
            normalized.set("embeddableFields", canonicalNamedFields(entityNode.path("embeddable-fields")));
            normalized.set("metadataFields", canonicalMetadataFields(entityNode.path("metadata-fields")));
            if (!entityNode.path("chunking").isMissingNode()) {
                normalized.set("chunking", entityNode.path("chunking").deepCopy());
            }
            entities.add(normalized);
        });
        return entities;
    }

    private ArrayNode canonicalNamedFields(JsonNode node) {
        ArrayNode out = objectMapper.createArrayNode();
        if (!node.isArray()) {
            return out;
        }
        List<ObjectNode> normalized = new ArrayList<>();
        node.forEach(item -> {
            if (item.isObject()) {
                ObjectNode next = objectMapper.createObjectNode();
                next.put("name", item.path("name").asText(""));
                if (!item.path("weight").isMissingNode()) {
                    next.put("weight", item.path("weight").asDouble());
                }
                normalized.add(next);
            }
        });
        normalized.stream()
            .sorted(Comparator.comparing(entry -> entry.path("name").asText("")))
            .forEach(out::add);
        return out;
    }

    private ArrayNode canonicalMetadataFields(JsonNode node) {
        ArrayNode out = objectMapper.createArrayNode();
        if (!node.isArray()) {
            return out;
        }
        List<ObjectNode> normalized = new ArrayList<>();
        node.forEach(item -> {
            if (item.isObject()) {
                ObjectNode next = objectMapper.createObjectNode();
                next.put("name", item.path("name").asText(""));
                next.put("type", item.path("type").asText("string"));
                normalized.add(next);
            }
        });
        normalized.stream()
            .sorted(Comparator.comparing(entry -> entry.path("name").asText("")))
            .forEach(out::add);
        return out;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash indexed output signature.", ex);
        }
    }
}
