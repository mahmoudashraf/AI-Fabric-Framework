package com.ai.behavior.schema;

import com.ai.behavior.config.BehaviorModuleProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class YamlBehaviorSchemaRegistryTest {

    private YamlBehaviorSchemaRegistry registry;

    @BeforeEach
    void setUp() {
        BehaviorModuleProperties properties = new BehaviorModuleProperties();
        properties.getSchemas().setPath("classpath:/behavior/test-schemas.yml");
        registry = new YamlBehaviorSchemaRegistry(properties);
        registry.loadDefinitions();
    }

    @Test
    void loadsDefinitionsFromClasspath() {
        assertThat(registry.getAll()).hasSize(2);
        assertThat(registry.find("tracking.session")).isPresent();
        assertThat(registry.getLastLoadedAt()).isAfter(Instant.EPOCH);
    }

    @Test
    void filtersByDomainWhenRequested() {
        assertThat(registry.getAll().stream().filter(def -> "support".equals(def.getDomain())))
            .hasSize(1);
        assertThat(registry.find("support.ticket")).isPresent();
    }
}
