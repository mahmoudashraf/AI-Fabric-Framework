package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AIWorkspaceConfigurationValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AIWorkspaceConfigurationValidator validator = new AIWorkspaceConfigurationValidator(
        new PlatformAIWorkspaceProperties(
            Path.of("."), false, 120, Duration.ofSeconds(60), null, null,
            "apps.loomai.test", "https://workspace.loomai.test")
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
              "knowledge":{"inventoryVectorSpace":"dealer-vehicle"},
              "presentation":{"imageHostAllowlist":["m.atcdn.co.uk"]}
            }
        """));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://cdn.example/logo.png",
        "https://cdn.example/brand/logo.svg?version=2&size=small",
        "https://cdn.example/brand%20assets/mark?signature=a%2Fb%3D",
        "https://cdn.example/brand%23mark.svg?revision=2",
        "https://cdn.example/image/transform?format=webp&width=96",
        "https://b\u00fccher.example/logo.svg",
        "HTTPS://CDN.Example:8443/logo",
        "https://cdn.example/",
        "https://brand-assets.invalid/logo.svg"
    })
    void acceptsExactHttpsLogoUrlsForBothExperiencesWithoutRewritingOrFetching(String logoUrl) throws Exception {
        for (boolean dealership : List.of(false, true)) {
            ObjectNode configuration = brandingConfiguration(dealership);
            configuration.putObject("theme").put("logoUrl", logoUrl).put("primaryColor", "#123b35");

            validateExperience(configuration, dealership);

            assertThat(configuration.path("theme").path("logoUrl").asText()).isEqualTo(logoUrl);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://cdn.example/logo.png",
        "javascript:alert(1)",
        "data:image/svg+xml,%3Csvg%3E%3C/svg%3E",
        "blob:https://cdn.example/logo",
        "file:///tmp/logo.png",
        "ftp://cdn.example/logo.png",
        "//cdn.example/logo.png",
        "/brand/logo.png",
        "https:logo.png",
        "https:///logo.png",
        "https://*.example/logo.png",
        "https://user:password@cdn.example/logo.png",
        "https://user@cdn.example/logo.png",
        "https://@cdn.example/logo.png",
        "https://cdn.example@attacker.example/logo.png",
        "https://cdn.example/logo.svg#mark",
        "https://cdn.example/logo.svg#",
        "https://cdn.example:99999/logo.png",
        "https://cdn.example/logo image.png",
        "https://cdn.example/logo.png ",
        " https://cdn.example/logo.png"
    })
    void rejectsUnsafeOrInexactLogoLocationsForBothExperiences(String logoUrl) throws Exception {
        for (boolean dealership : List.of(false, true)) {
            ObjectNode configuration = brandingConfiguration(dealership);
            configuration.putObject("theme").put("logoUrl", logoUrl);

            assertThatThrownBy(() -> validateExperience(configuration, dealership))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("theme.logoUrl");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "null", "true", "123", "[]", "{}", "\"\"", "\"   \"" })
    void rejectsNonStringOrEmptyLogoValues(String jsonValue) throws Exception {
        for (boolean dealership : List.of(false, true)) {
            ObjectNode configuration = brandingConfiguration(dealership);
            configuration.putObject("theme").set("logoUrl", objectMapper.readTree(jsonValue));

            assertThatThrownBy(() -> validateExperience(configuration, dealership))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("theme.logoUrl");
        }
    }

    @Test
    void keepsLogoOptionalAndBoundsItsLength() throws Exception {
        String prefix = "https://cdn.example/";
        String boundedUrl = prefix + "a".repeat(2048 - prefix.length());
        for (boolean dealership : List.of(false, true)) {
            ObjectNode configuration = brandingConfiguration(dealership);
            validateExperience(configuration, dealership);
            ObjectNode theme = configuration.putObject("theme");
            validateExperience(configuration, dealership);
            theme.put("logoUrl", boundedUrl);
            validateExperience(configuration, dealership);
            theme.put("logoUrl", boundedUrl + "a");

            assertThatThrownBy(() -> validateExperience(configuration, dealership))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("theme.logoUrl");
        }
    }

    private ObjectNode brandingConfiguration(boolean dealership) throws Exception {
        return (ObjectNode) objectMapper.readTree(dealership ? """
            {
              "dealer":{"id":"dealer-1","assistantLabel":"Brand assistant"},
              "page":{"kind":"auto","rootSelector":"main","contextLabel":"Current page"}
            }
            """ : """
            {"app":{"assistantLabel":"Brand assistant"}}
            """);
    }

    private void validateExperience(ObjectNode configuration, boolean dealership) {
        if (dealership) validator.validateDealershipConfiguration(configuration);
        else validator.validateGenericConfiguration(configuration);
    }

    @Test
    void rejectsBrowserOwnedRetrievalRoutingConfiguration() throws Exception {
        assertThatThrownBy(() -> validator.validateDealershipConfiguration(objectMapper.readTree("""
            {
              "dealer":{"id":"dealer-1","assistantLabel":"Northfield AI"},
              "page":{"kind":"auto","rootSelector":"main","contextLabel":"Current page"},
              "knowledge":{"retrievalVectorSpaces":["dealer-vehicle","document"]}
            }
            """)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Unknown knowledge field: retrievalVectorSpaces");
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
