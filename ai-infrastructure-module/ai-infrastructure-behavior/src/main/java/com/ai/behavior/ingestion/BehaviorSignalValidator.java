package com.ai.behavior.ingestion;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.exception.BehaviorValidationException;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.AttributeType;
import com.ai.behavior.schema.BehaviorSchemaRegistry;
import com.ai.behavior.schema.BehaviorSignalAttributeDefinition;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BehaviorSignalValidator {

    private final BehaviorSchemaRegistry schemaRegistry;
    private final BehaviorModuleProperties properties;

    public void validate(BehaviorSignal signal) {
        if (signal == null) {
            throw new BehaviorValidationException("Behavior signal cannot be null");
        }
        if (signal.getUserId() == null && !StringUtils.hasText(signal.getSessionId())) {
            throw new BehaviorValidationException("Either userId or sessionId must be provided");
        }
        if (!StringUtils.hasText(signal.getSchemaId())) {
            throw new BehaviorValidationException("schemaId is required");
        }

        BehaviorSignalDefinition definition = schemaRegistry.getRequired(signal.getSchemaId());
        Map<String, Object> attributes = signal.safeAttributes();
        int maxAttributes = properties.getSchemas().getMaxAttributeCount();
        if (attributes.size() > maxAttributes) {
            throw new BehaviorValidationException("attributes contains more than " + maxAttributes + " keys");
        }

        for (BehaviorSignalAttributeDefinition attribute : definition.getAttributes()) {
            Object value = attributes.get(attribute.getName());
            if (attribute.isRequired() && value == null) {
                throw new BehaviorValidationException("Missing required attribute '" + attribute.getName() + "'");
            }
            if (value != null) {
                validateType(attribute, value);
                validateBounds(attribute, value);
            }
        }

        if (signal.getTimestamp() == null) {
            signal.setTimestamp(LocalDateTime.now());
        }
        if (signal.getIngestedAt() == null) {
            signal.setIngestedAt(LocalDateTime.now());
        }
        if (!StringUtils.hasText(signal.getVersion())) {
            signal.setVersion(definition.getVersion());
        }
    }

    private void validateType(BehaviorSignalAttributeDefinition attribute, Object value) {
        AttributeType type = attribute.getType() == null ? AttributeType.STRING : attribute.getType();
        boolean valid;
        switch (type) {
            case STRING -> valid = value instanceof String;
            case NUMBER -> valid = value instanceof Number;
            case INTEGER -> valid = value instanceof Integer || value instanceof Long;
            case BOOLEAN -> valid = value instanceof Boolean;
            case OBJECT -> valid = value instanceof Map;
            case ARRAY -> valid = value instanceof Iterable;
            default -> valid = true;
        }
        if (!valid) {
            throw new BehaviorValidationException("Attribute '%s' must be of type %s".formatted(attribute.getName(), type));
        }
    }

    private void validateBounds(BehaviorSignalAttributeDefinition attribute, Object value) {
        if (value == null) {
            return;
        }
        if (attribute.getMaxLength() != null && value instanceof String str && str.length() > attribute.getMaxLength()) {
            throw new BehaviorValidationException("Attribute '%s' exceeds max length %d"
                .formatted(attribute.getName(), attribute.getMaxLength()));
        }
        if (value instanceof Number number) {
            if (attribute.getMinimum() != null && number.doubleValue() < attribute.getMinimum()) {
                throw new BehaviorValidationException("Attribute '%s' must be >= %s"
                    .formatted(attribute.getName(), attribute.getMinimum()));
            }
            if (attribute.getMaximum() != null && number.doubleValue() > attribute.getMaximum()) {
                throw new BehaviorValidationException("Attribute '%s' must be <= %s"
                    .formatted(attribute.getName(), attribute.getMaximum()));
            }
        }
        if (attribute.getEnumValues() != null && !attribute.getEnumValues().isEmpty() && value instanceof String str) {
            boolean match = attribute.getEnumValues().stream().anyMatch(v -> v.equalsIgnoreCase(str));
            if (!match) {
                throw new BehaviorValidationException("Attribute '%s' must be one of %s"
                    .formatted(attribute.getName(), attribute.getEnumValues()));
            }
        }
    }
}
