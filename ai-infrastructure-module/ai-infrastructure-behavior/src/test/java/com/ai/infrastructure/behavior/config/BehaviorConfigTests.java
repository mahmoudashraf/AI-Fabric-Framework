package com.ai.infrastructure.behavior.config;

import com.ai.infrastructure.behavior.entity.BehaviorInsights;
import com.ai.infrastructure.relationship.service.EntityRelationshipMapper;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

class BehaviorConfigTests {

    @Test
    void autoConfigurationLoadsPresetWithoutOverride() {
        AIEntityConfigurationLoader loader = Mockito.mock(AIEntityConfigurationLoader.class);
        BehaviorAIAutoConfiguration config = new BehaviorAIAutoConfiguration(loader);
        ReflectionTestUtils.setField(config, "mode", "FULL");

        config.registerBehaviorConfig();

        verify(loader).loadConfigurationFromFile("classpath:behavior-presets/behavior-ai-full.yml", false);
    }

    @Test
    void relationshipRegistrationRegistersEntity() {
        EntityRelationshipMapper mapper = Mockito.mock(EntityRelationshipMapper.class);
        BehaviorRelationshipRegistration registration = new BehaviorRelationshipRegistration(mapper);

        registration.registerRelationships();

        verify(mapper).registerEntityType(BehaviorInsights.class);
        assertThat(true).isTrue(); // placeholder to keep AssertJ usage consistent
    }
}
