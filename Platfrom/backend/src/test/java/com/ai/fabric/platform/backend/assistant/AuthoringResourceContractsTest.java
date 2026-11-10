package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthoringResourceContractsTest {
    @Test void sharedEngageGuidanceHasNoIndustrySpecificPaths() {
        var mapper = new ObjectMapper(); var catalogue = mapper.createObjectNode();
        AuthoringResourceContracts.populate(mapper, catalogue, "ENGAGE");
        assertThat(catalogue.path("engageFieldRules").asText()).contains("selectedExperiencePack.fieldPaths")
            .doesNotContain("dealership", "configuration.dealer", "configuration.app");
    }
    @Test void engageExamplesPassTheExistingPublicPackValidator() {
        var mapper = new ObjectMapper(); var catalogue = mapper.createObjectNode();
        AuthoringResourceContracts.populate(mapper, catalogue, "ENGAGE");
        var configuration = mapper.createObjectNode();
        configuration.putObject("app").put("assistantLabel", "Example support");
        configuration.set("toolGroups", catalogue.path("engageToolGroupsExample"));
        configuration.set("toolRail", catalogue.path("engageToolRailExample"));
        var validator = new com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceConfigurationValidator(
            new com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties(null, false, 0, null, null, null, null, null));
        assertThatCode(() -> validator.validateGenericConfiguration(configuration)).doesNotThrowAnyException();
    }
    @Test void dataPluginGuidanceUsesDatasetIdentityInsteadOfArrayIndexPointers() {
        var mapper = new ObjectMapper(); var catalogue = mapper.createObjectNode();
        AuthoringResourceContracts.populate(mapper, catalogue, "DATA_PLUGIN");
        assertThat(catalogue.at("/collectionShapes/~1manifest~1contributions~1datasets/identity").asText()).isEqualTo("datasetId");
        assertThat(catalogue.at("/datasetPolicyChangeExample/operation").asText()).isEqualTo("UPSERT");
        assertThat(catalogue.at("/datasetPolicyChangeExample/path").asText()).isEqualTo("/manifest/contributions/datasets");
    }
    @Test void machineGuidanceDistinguishesObjectRoutesFromNamedActionArrays() {
        var mapper = new ObjectMapper(); var catalogue = mapper.createObjectNode();
        AuthoringResourceContracts.populate(mapper, catalogue, "ACTIONS");
        assertThat(catalogue.path("contractVersion").asText()).isEqualTo("loomai-authoring-catalogue-v1");
        assertThat(catalogue.path("collectionShapes").path("/actionsConfig/actions").path("type").asText()).isEqualTo("array");
        assertThat(catalogue.path("collectionShapes").path("/routingConfig/actions").path("type").asText()).isEqualTo("object");
        assertThat(catalogue.at("/actionFieldChangeExample/operation").asText()).isEqualTo("UPSERT");
        assertThat(catalogue.at("/actionFieldChangeExample/path").asText()).isEqualTo("/actionsConfig/actions");
        var example = catalogue.path("routeChangeExample");
        assertThat(example.path("operation").asText()).isEqualTo("SET");
        assertThat(example.at("/value/response/success-http-status").isArray()).isTrue();
        assertThat(example.at("/value/response/success-http-status/0").asInt()).isEqualTo(200);
        assertThat(example.path("path").asText()).isEqualTo("/routingConfig/actions/lookup_record");
    }
}
