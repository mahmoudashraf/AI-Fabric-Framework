package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AIWorkspaceConfigurationValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AIWorkspaceConfigurationValidator validator = new AIWorkspaceConfigurationValidator(
        new PlatformAIWorkspaceProperties(Path.of("."), false, 120, Duration.ofSeconds(60), null, null)
    );

    @Test
    void normalizesExactHttpsOriginsAndRejectsWildcardOrPathOrigins() {
        assertThat(validator.normalizeOrigins(List.of("https://Dealer.Example", "https://dealer.example")))
            .containsExactly("https://dealer.example");

        assertThatThrownBy(() -> validator.normalizeOrigins(List.of("https://*.dealer.example")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("exact HTTPS origins");
        assertThatThrownBy(() -> validator.normalizeOrigins(List.of("https://dealer.example/path")))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("exact HTTPS origins");
    }

    @Test
    void rejectsSecretsAndArbitraryRuntimeOrScriptLocations() throws Exception {
        for (String json : List.of(
            "{\"apiKey\":\"secret\"}",
            "{\"runtimeUrl\":\"https://attacker.example\"}",
            "{\"scriptUrl\":\"https://attacker.example/code.js\"}",
            "{\"label\":\"<script>alert(1)</script>\"}"
        )) {
            assertThatThrownBy(() -> validator.validatePublicConfiguration(objectMapper.readTree(json)))
                .isInstanceOf(ResponseStatusException.class);
        }
    }

    @Test
    void acceptsBoundedTypedDealershipConfiguration() throws Exception {
        validator.validateDealershipConfiguration(objectMapper.readTree("""
            {
              "dealer":{"id":"dealer-1","assistantLabel":"Northfield AI"},
              "page":{"kind":"auto","rootSelector":"main","contextLabel":"Current page","maxChars":1800,"maxPages":3,"maxTotalChars":10000},
              "knowledge":{"retrievalVectorSpaces":["dealer-vehicle","dealership-document"]},
              "presentation":{"imageHostAllowlist":["m.atcdn.co.uk"]}
            }
        """));
    }

    @Test
    void rejectsUnknownOrMistypedDealershipFields() throws Exception {
        assertThatThrownBy(() -> validator.validateDealershipConfiguration(objectMapper.readTree("""
            {
              "dealer":{"id":"dealer-1","assistantLabel":"Northfield AI"},
              "page":{"kind":"auto","rootSelector":"main","contextLabel":"Current page"},
              "capabilites":{"testDrive":true}
            }
            """)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unknown configuration field");

        assertThatThrownBy(() -> validator.validateDealershipConfiguration(objectMapper.readTree("""
            {
              "dealer":{"id":"dealer-1","assistantLabel":"Northfield AI"},
              "page":{"kind":"auto","rootSelector":"main","contextLabel":"Current page","maxPages":"three"}
            }
            """)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("attachment limits");
    }
}
