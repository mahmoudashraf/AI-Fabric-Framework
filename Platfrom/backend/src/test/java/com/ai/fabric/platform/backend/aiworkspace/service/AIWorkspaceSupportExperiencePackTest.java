package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetDescriptor;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AIWorkspaceSupportExperiencePackTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final AIWorkspaceConfigurationValidator validator = new AIWorkspaceConfigurationValidator(
        new PlatformAIWorkspaceProperties(Path.of("."), false, 120, Duration.ofSeconds(60), null, null,
            "apps.loomai.test", "https://workspace.loomai.test"));

    @Test void acceptsDisabledUnboundDraftAndBoundTicketsWithoutHumanHandover() throws Exception {
        var config = draft();
        validator.validateSupportConfiguration(config);
        config.with("capabilities").put("tickets", true);
        config.with("support").put("spaceId", "space-example").put("ticketProviderBindingId", "tpb-example")
            .put("requesterOrigin", "https://desk.example.test");
        config.with("support").putArray("topics").addObject().put("code", "setup").put("label", "Setup");
        validator.validateSupportConfiguration(config);
        assertThat(config.at("/capabilities/handover").asBoolean()).isFalse();
    }

    @Test void registryOnlyEnablesCatalogueWhenTheExactAssetIsPackaged() {
        var assets = mock(AIWorkspaceAssetCatalogService.class);
        when(assets.experiencePack("support", "1.0.0")).thenThrow(new IllegalStateException("Missing"));
        var packs = new AIWorkspaceExperiencePackRegistry(assets, validator);
        assertThat(packs.resolve("support", "1.0.0").configurationSchemaVersion())
            .isEqualTo("loomai-support-experience-config-v1");
        assertThat(packs.summaries().stream().filter(p -> p.code().equals("support")).findFirst().orElseThrow().enabled()).isFalse();
        var asset = mock(AIWorkspaceAssetDescriptor.class);
        doReturn(asset).when(assets).experiencePack("support", "1.0.0");
        assertThat(packs.summaries().stream().filter(p -> p.code().equals("support")).findFirst().orElseThrow().enabled()).isTrue();
        assertThatThrownBy(() -> packs.resolve("support", "1.0.1")).isInstanceOf(ResponseStatusException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"spaceId", "ticketProviderBindingId"})
    void draftTicketPreferenceDoesNotRequireCustomerEnteredManagedReferences(String missing) throws Exception {
        var config = draft(); config.with("capabilities").put("tickets", true);
        config.with("support").put("spaceId", "space-example").put("ticketProviderBindingId", "tpb-example").remove(missing);
        validator.validateSupportConfiguration(config);
        config.with("support").put(missing, "");
        validator.validateSupportConfiguration(config);
    }

    @ParameterizedTest @ValueSource(strings = {
        "http://desk.example.test", "https://desk.example.test/api", "https://user:pass@desk.example.test",
        "https://desk.example.test?token=x", "https://desk.example.test#fragment", "https://*.example.test",
        "https://desk.example.test/", "https://Desk.Example.Test"
    })
    void refusesUnreviewedDestinationsOrNoncanonicalOrigins(String origin) throws Exception {
        var config = draft(); config.with("support").put("requesterOrigin", origin);
        assertThatThrownBy(() -> validator.validateSupportConfiguration(config)).isInstanceOf(ResponseStatusException.class);
    }

    @ParameterizedTest @ValueSource(strings = {"requesterId", "providerApiKey", "endpoint", "ticketProvider", "capabilities", "operatorRoles"})
    void refusesAuthorityCredentialsAndSelfAssertedProviderCapabilities(String field) throws Exception {
        var config = draft(); config.with("support").put(field, "untrusted");
        assertThatThrownBy(() -> validator.validateSupportConfiguration(config)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void rejectsWrongSchemaBooleanTypeAndDuplicateTopicCodes() throws Exception {
        var config = draft(); config.put("schemaVersion", "other");
        assertThatThrownBy(() -> validator.validateSupportConfiguration(config)).isInstanceOf(ResponseStatusException.class);
        config.put("schemaVersion", AIWorkspaceExperiencePackRegistry.SUPPORT_SCHEMA);
        config.with("capabilities").put("tickets", "true");
        assertThatThrownBy(() -> validator.validateSupportConfiguration(config)).isInstanceOf(ResponseStatusException.class);
        config.with("capabilities").put("tickets", false);
        var topics = config.with("support").putArray("topics");
        topics.addObject().put("code", "setup").put("label", "Setup");
        topics.addObject().put("code", "setup").put("label", "Duplicate");
        assertThatThrownBy(() -> validator.validateSupportConfiguration(config)).isInstanceOf(ResponseStatusException.class);
    }

    private ObjectNode draft() throws Exception {
        return (ObjectNode) mapper.readTree("""
            {"schemaVersion":"loomai-support-experience-config-v1","app":{"assistantLabel":"Acme Support","title":"Help and support"},
             "support":{"requestLabel":"My requests","topics":[],"teamVisible":false},
             "capabilities":{"tickets":false,"handover":false,"attachments":true,"currentPage":false}}
            """);
    }
}
