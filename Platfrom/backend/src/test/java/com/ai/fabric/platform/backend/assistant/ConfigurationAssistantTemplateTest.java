package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ConfigurationAssistantTemplateTest {
    @Test void apiPreparationGuidanceIsEditableProfileConfigurationWithoutExpandingOtherProfiles() {
        var behavior = new ConfigurationAssistantTemplate(new ObjectMapper()).behavior();
        var workflow = behavior.path("workflowAssistance");
        assertThat(workflow.has("apiPreparationInstructions")).isFalse();
        assertThat(workflow.path("profiles")).hasSize(9);
        for (var profile : workflow.path("profiles")) {
            if (java.util.List.of("api", "setup").contains(profile.path("id").asText()))
                assertThat(profile.path("instructions").asText()).contains("API CONTRACT COMPLETENESS", "request.query", "2xx", "sourceCoverage.complete");
            else assertThat(profile.path("instructions").asText()).doesNotContain("API CONTRACT COMPLETENESS");
            if ("guide".equals(profile.path("id").asText())) assertThat(profile.path("writeActions")).isEmpty();
        }
        assertThat(workflow.path("goal").path("enabled").asBoolean()).isTrue();
    }
}
