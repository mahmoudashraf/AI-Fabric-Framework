package com.ai.fabric.vectorization.mapping;

import com.ai.fabric.integration.json.JsonNavigation;
import com.ai.fabric.vectorization.model.VectorizationMappedRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

public class VectorizationRecordMapper {

    private final ObjectMapper objectMapper;

    public VectorizationRecordMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public VectorizationMappedRecord map(String entityType, JsonNode mappingConfig, JsonNode sourceRecord) {
        JsonNode entityMapping = entityMapping(mappingConfig, entityType);
        String targetEntityType = targetEntityType(entityType, entityMapping, sourceRecord);
        String idField = text(entityMapping, "recordIdField", "id");
        String recordId = JsonNavigation.asText(sourceRecord, idField);
        if (!StringUtils.hasText(recordId)) {
            throw new IllegalArgumentException("Mapped record is missing a stable id for entity '" + entityType + "'.");
        }
        String recordVersion = JsonNavigation.asText(sourceRecord, text(entityMapping, "recordVersionField", null));

        Map<String, Object> entity = mappedObject(sourceRecord, entityMapping.path("entityFieldMappings"));
        if (entity.isEmpty() && sourceRecord.isObject()) {
            entity = objectMapper.convertValue(sourceRecord, Map.class);
        }
        Map<String, Object> metadata = mappedObject(sourceRecord, entityMapping.path("metadataFieldMappings"));
        metadata.putAll(staticObject(entityMapping.path("metadataStaticValues")));
        metadata.putAll(staticObjectForTarget(entityMapping.path("metadataStaticValuesByTargetEntityType"), targetEntityType));

        return new VectorizationMappedRecord(
            targetEntityType,
            recordId,
            recordId,
            recordVersion,
            entity,
            metadata
        );
    }

    private Map<String, Object> mappedObject(JsonNode sourceRecord, JsonNode mappings) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (mappings != null && mappings.isObject()) {
            JsonNavigation.fields(mappings).forEachRemaining(entry -> {
                JsonNode sourceValue = JsonNavigation.readPath(sourceRecord, entry.getValue().asText(""));
                if (sourceValue == null || sourceValue.isMissingNode() || sourceValue.isNull()) {
                    return;
                }
                values.put(entry.getKey(), objectMapper.convertValue(sourceValue, Object.class));
            });
        }
        return values;
    }

    private Map<String, Object> staticObject(JsonNode valuesNode) {
        if (valuesNode == null || !valuesNode.isObject()) {
            return Map.of();
        }
        return objectMapper.convertValue(valuesNode, Map.class);
    }

    private Map<String, Object> staticObjectForTarget(JsonNode valuesByTargetNode, String targetEntityType) {
        if (valuesByTargetNode == null || !valuesByTargetNode.isObject() || !StringUtils.hasText(targetEntityType)) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        JsonNode wildcard = valuesByTargetNode.path("*");
        if (wildcard.isObject()) {
            values.putAll(staticObject(wildcard));
        }
        JsonNode targetSpecific = valuesByTargetNode.path(targetEntityType.trim());
        if (targetSpecific.isObject()) {
            values.putAll(staticObject(targetSpecific));
        }
        return values;
    }

    private JsonNode entityMapping(JsonNode mappingConfig, String entityType) {
        if (mappingConfig == null || mappingConfig.isMissingNode() || mappingConfig.isNull()) {
            return objectMapper.createObjectNode();
        }
        JsonNode entityMappings = mappingConfig.path("entityMappings");
        JsonNode mapping = entityMappings.path(entityType);
        return mapping.isObject() ? mapping : objectMapper.createObjectNode();
    }

    private String targetEntityType(String entityType, JsonNode entityMapping, JsonNode sourceRecord) {
        String configuredField = text(entityMapping, "targetEntityTypeField", null);
        if (!StringUtils.hasText(configuredField)) {
            configuredField = text(entityMapping, "vectorSpaceField", null);
        }
        if (StringUtils.hasText(configuredField)) {
            String target = JsonNavigation.asText(sourceRecord, configuredField);
            if (!StringUtils.hasText(target)) {
                throw new IllegalArgumentException(
                    "Mapped record is missing target entity type field '" + configuredField + "' for entity '" + entityType + "'."
                );
            }
            return target.trim();
        }
        String staticTarget = text(entityMapping, "targetEntityType", null);
        return StringUtils.hasText(staticTarget) ? staticTarget.trim() : entityType;
    }

    private String text(JsonNode node, String fieldName, String fallback) {
        if (node != null && node.isObject() && StringUtils.hasText(node.path(fieldName).asText(null))) {
            return node.path(fieldName).asText().trim();
        }
        return fallback;
    }
}
