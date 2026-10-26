package com.ai.fabric.platform.backend.desk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class DeskServiceClientTest {
    @Test void requiresAnExplicitConfiguredEnvironmentAndPrivateServiceCredential() {
        var client = new DeskServiceClient(new MockEnvironment(), new ObjectMapper());
        assertThatThrownBy(() -> client.connection("production")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> client.connection("development")).isInstanceOf(ResponseStatusException.class);
    }

    @Test void rejectsUntrustedUrlShapesAndNeverPrintsTheCredential() {
        for (var url : new String[]{"http://desk.example", "https://user:password@desk.example", "https://desk.example/path", "https://desk.example?next=evil"}) {
            var client = new DeskServiceClient(environment(url), new ObjectMapper());
            assertThatThrownBy(() -> client.connection("production")).isInstanceOf(ResponseStatusException.class);
        }
        var client = new DeskServiceClient(environment("https://desk.example/"), new ObjectMapper());
        assertThat(client.connection("production").baseUrl()).isEqualTo("https://desk.example");
        assertThat(client.connection("production").toString()).doesNotContain("credential-that-must-not-be-logged");
    }

    private MockEnvironment environment(String url) {
        return new MockEnvironment().withProperty("LOOMAI_DESK_PRODUCTION_BASE_URL", url)
            .withProperty("LOOMAI_DESK_PRODUCTION_SERVICE_KEY", "credential-that-must-not-be-logged-1234567890");
    }
}
