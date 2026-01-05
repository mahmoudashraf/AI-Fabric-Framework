package com.ai.infrastructure.chat.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.Map;

/**
 * JPA converter to persist metadata maps as JSON text.
 */
@Converter
@Slf4j
public class MetadataConverter implements AttributeConverter<Map<String, Object>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        try {
            if (attribute == null || attribute.isEmpty()) {
                return "{}";
            }
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (Exception ex) {
            log.warn("Unable to serialize metadata: {}", ex.getMessage());
            return "{}";
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        try {
            if (dbData == null || dbData.isBlank()) {
                return Collections.emptyMap();
            }
            return OBJECT_MAPPER.readValue(dbData, MAP_TYPE);
        } catch (Exception ex) {
            log.warn("Unable to deserialize metadata: {}", ex.getMessage());
            return Collections.emptyMap();
        }
    }
}
