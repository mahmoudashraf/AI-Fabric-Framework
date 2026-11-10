package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuthoringResourceContractsTest {
    @Test void machineGuidanceDistinguishesObjectRoutesFromNamedActionArrays() {
        var mapper = new ObjectMapper(); var catalogue = mapper.createObjectNode();
        AuthoringResourceContracts.populate(mapper, catalogue, "ACTIONS");
        assertThat(catalogue.path("contractVersion").asText()).isEqualTo("loomai-authoring-catalogue-v1");
        assertThat(catalogue.path("collectionShapes").path("/actionsConfig/actions").path("type").asText()).isEqualTo("array");
        assertThat(catalogue.path("collectionShapes").path("/routingConfig/actions").path("type").asText()).isEqualTo("object");
        var example = catalogue.path("routeChangeExample");
        assertThat(example.path("operation").asText()).isEqualTo("SET");
        assertThat(example.at("/value/response/success-http-status").isArray()).isTrue();
        assertThat(example.at("/value/response/success-http-status/0").asInt()).isEqualTo(200);
        assertThat(example.path("path").asText()).isEqualTo("/routingConfig/actions/lookup_record");
    }
}
