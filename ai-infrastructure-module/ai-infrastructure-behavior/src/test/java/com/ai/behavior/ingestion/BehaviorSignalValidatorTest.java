package com.ai.behavior.ingestion;

import com.ai.behavior.config.BehaviorModuleProperties;
import com.ai.behavior.exception.BehaviorValidationException;
import com.ai.behavior.model.BehaviorSignal;
import com.ai.behavior.schema.YamlBehaviorSchemaRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BehaviorSignalValidatorTest {

    private BehaviorSignalValidator validator;

    @BeforeEach
    void setUp() {
        BehaviorModuleProperties properties = new BehaviorModuleProperties();
        properties.getSchemas().setPath("classpath:/behavior/schemas/default-schemas.yml");
        YamlBehaviorSchemaRegistry registry = new YamlBehaviorSchemaRegistry(properties);
        registry.loadDefinitions();
        validator = new BehaviorSignalValidator(registry, properties);
    }

    @Test
    void validateAllowsSchemaCompliantSignal() {
        BehaviorSignal signal = BehaviorSignal.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .schemaId("engagement.view")
            .timestamp(LocalDateTime.now())
            .attributes(Map.of("device", "web"))
            .build();

        assertThatCode(() -> validator.validate(signal)).doesNotThrowAnyException();
    }

    @Test
    void validateRejectsMissingRequiredAttribute() {
        BehaviorSignal signal = BehaviorSignal.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .schemaId("intent.search")
            .timestamp(LocalDateTime.now())
            .attributes(Map.of())
            .build();

        assertThatThrownBy(() -> validator.validate(signal))
            .isInstanceOf(BehaviorValidationException.class)
            .hasMessageContaining("Missing required attribute");
    }

    @Test
    void validateRejectsInvalidEnumValues() {
        BehaviorSignal signal = BehaviorSignal.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .schemaId("engagement.view")
            .timestamp(LocalDateTime.now())
            .attributes(Map.of("device", "console"))
            .build();

        assertThatThrownBy(() -> validator.validate(signal))
            .isInstanceOf(BehaviorValidationException.class)
            .hasMessageContaining("must be one of");
    }
}
