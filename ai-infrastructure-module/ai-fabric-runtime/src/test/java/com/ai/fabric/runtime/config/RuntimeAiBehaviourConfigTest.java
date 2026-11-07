package com.ai.fabric.runtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeAiBehaviourConfigTest {
    @Test
    void unconfiguredRuntimeRetainsExistingSuggestionPolicy() {
        assertThat(load("{\"answerGenerationPrompt\":\"Original guidance\"}").currentManagedNextStepsEnabled()).isNull();
    }

    @Test
    void explicitOffAndContextualUseTheDeploymentOwnedProfile() {
        for (String id : new String[]{"off", "contextual", "custom"}) {
            var service = load("{\"answerGenerationPrompt\":\"Compiled profile guidance\",\"aiBehaviour\":{\"schemaVersion\":\"loomai-ai-behaviour-v1\",\"nextSteps\":{\"presetId\":\"" + id + "\"}}}");
            assertThat(service.currentManagedNextStepsEnabled()).isEqualTo(!"off".equals(id));
            assertThat(service.currentPromptOverlay()).containsEntry("answerGenerationPrompt", "Compiled profile guidance");
            assertThat(service.currentPromptOverlay()).doesNotContainKey("aiBehaviour");
        }
    }

    @Test
    void reloadWithoutConfigClearsManagedNextStepPreference() {
        var properties = new RuntimeDeploymentPromptConfigProperties();
        properties.setConfigFile("classpath:fixture.json");
        var service = service(properties, "{\"aiBehaviour\":{\"schemaVersion\":\"loomai-ai-behaviour-v1\",\"nextSteps\":{\"presetId\":\"off\"}}}");
        service.load();
        assertThat(service.currentManagedNextStepsEnabled()).isFalse();
        properties.setConfigFile("");
        service.load();
        assertThat(service.currentManagedNextStepsEnabled()).isNull();
    }

    private RuntimeDeploymentPromptConfigService load(String json) {
        var properties = new RuntimeDeploymentPromptConfigProperties();
        properties.setConfigFile("classpath:fixture.json");
        var service = service(properties, json);
        service.load();
        return service;
    }

    private RuntimeDeploymentPromptConfigService service(RuntimeDeploymentPromptConfigProperties properties, String json) {
        var loader = new DefaultResourceLoader() {
            @Override public Resource getResource(String location) {
                return new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8));
            }
        };
        return new RuntimeDeploymentPromptConfigService(properties, loader, new ObjectMapper());
    }
}
