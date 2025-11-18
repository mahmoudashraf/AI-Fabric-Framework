package com.ai.behavior.schema;

import com.ai.behavior.config.BehaviorModuleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YamlBehaviorSchemaRegistryTest {

    private YamlBehaviorSchemaRegistry registry;

    @BeforeEach
    void setUp() {
        BehaviorModuleProperties properties = new BehaviorModuleProperties();
        properties.getSchemas().setPath("classpath:/behavior/schemas/default-schemas.yml");
        registry = new YamlBehaviorSchemaRegistry(properties);
        registry.loadDefinitions();
    }

    @Test
    void loadsDefinitionsFromYaml() {
        assertThat(registry.getAll()).isNotEmpty();
        assertThat(registry.find("engagement.view")).isPresent();
    }

    @Test
    void getRequiredThrowsForUnknownSchema() {
        assertThatThrownBy(() -> registry.getRequired("unknown.schema"))
            .isInstanceOf(SchemaValidationException.class);
    }
}
