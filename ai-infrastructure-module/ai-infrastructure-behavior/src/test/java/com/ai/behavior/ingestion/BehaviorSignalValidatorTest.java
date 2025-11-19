package com.ai.behavior.ingestion;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.exception.BehaviorValidationException;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.AttributeType;
import com.ai.behavior.schema.BehaviorSchemaRegistry;
import com.ai.behavior.schema.BehaviorSignalAttributeDefinition;
import com.ai.behavior.schema.BehaviorSignalDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BehaviorSignalValidatorTest {

    private BehaviorSignalValidator validator;

    @BeforeEach
    void setUp() {
        BehaviorModuleProperties properties = new BehaviorModuleProperties();
        properties.getSchemas().setMaxAttributeCount(5);
        validator = new BehaviorSignalValidator(inMemoryRegistry(), properties);
    }

    @Test
    void validatesWellFormedSignals() {
        BehaviorSignal signal = baseSignalBuilder()
            .attributes(Map.of("amount", 42.5, "status", "OPEN"))
            .build();

        validator.validate(signal);

        assertThat(signal.getTimestamp()).isNotNull();
        assertThat(signal.getIngestedAt()).isNotNull();
        assertThat(signal.getVersion()).isEqualTo("1.0");
    }

    @Test
    void rejectsMissingRequiredAttributes() {
        BehaviorSignal signal = baseSignalBuilder()
            .attributes(Map.of("status", "OPEN"))
            .build();

        assertThatThrownBy(() -> validator.validate(signal))
            .isInstanceOf(BehaviorValidationException.class)
            .hasMessageContaining("Missing required attribute 'amount'");
    }

    @Test
    void rejectsTypeMismatch() {
        BehaviorSignal signal = baseSignalBuilder()
            .attributes(Map.of("amount", "forty-two", "status", "OPEN"))
            .build();

        assertThatThrownBy(() -> validator.validate(signal))
            .isInstanceOf(BehaviorValidationException.class)
            .hasMessageContaining("must be of type NUMBER");
    }

    @Test
    void enforcesEnumValuesCaseInsensitive() {
        BehaviorSignal signal = baseSignalBuilder()
            .attributes(Map.of("amount", 10, "status", "invalid"))
            .build();

        assertThatThrownBy(() -> validator.validate(signal))
            .isInstanceOf(BehaviorValidationException.class)
            .hasMessageContaining("must be one of");
    }

    private BehaviorSignal.BehaviorSignalBuilder baseSignalBuilder() {
        return BehaviorSignal.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .schemaId("test.event")
            .timestamp(LocalDateTime.now());
    }

    private BehaviorSchemaRegistry inMemoryRegistry() {
        BehaviorSignalAttributeDefinition amount = new BehaviorSignalAttributeDefinition();
        amount.setName("amount");
        amount.setType(AttributeType.NUMBER);
        amount.setRequired(true);
        amount.setMinimum(0.0);

        BehaviorSignalAttributeDefinition status = new BehaviorSignalAttributeDefinition();
        status.setName("status");
        status.setType(AttributeType.STRING);
        status.setRequired(true);
        status.setEnumValues(List.of("OPEN", "CLOSED"));

        BehaviorSignalDefinition definition = new BehaviorSignalDefinition();
        definition.setId("test.event");
        definition.setVersion("1.0");
        definition.setAttributes(List.of(amount, status));

        return new BehaviorSchemaRegistry() {
            @Override
            public Optional<BehaviorSignalDefinition> find(String schemaId) {
                return "test.event".equals(schemaId) ? Optional.of(definition) : Optional.empty();
            }

            @Override
            public BehaviorSignalDefinition getRequired(String schemaId) {
                return find(schemaId).orElseThrow();
            }

            @Override
            public Collection<BehaviorSignalDefinition> getAll() {
                return List.of(definition);
            }
        };
    }
}
