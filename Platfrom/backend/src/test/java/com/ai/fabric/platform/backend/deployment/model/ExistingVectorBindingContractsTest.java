package com.ai.fabric.platform.backend.deployment.model;

import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.AuthenticationType;
import com.ai.fabric.platform.backend.deployment.model.ExistingVectorBindingContracts.Request;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExistingVectorBindingContractsTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void legacyJavaConstructorExplicitlySelectsUsernamePassword() {
        var request = new Request("version", "cluster", "project", "region", "Free", "default", "user", "fake-password", "approved");
        assertThat(request.authType()).isEqualTo(AuthenticationType.USERNAME_PASSWORD);
        assertThat(request.token()).isNull();
        assertThat(request.toString()).doesNotContain("fake-password", "user");
    }

    @ParameterizedTest @ValueSource(strings = {"{}", "{\"token\":\"fake-token\"}", "{\"authType\":null}",
        "{\"username\":\"user\",\"password\":\"fake-password\"}"})
    void jsonCannotSilentlyUseLegacyJavaConstructor(String json) {
        assertThatThrownBy(() -> mapper.readValue(json, Request.class)).hasMessageContaining("EXTERNAL_VECTOR_AUTH_TYPE_REQUIRED");
    }

    @Test void jsonUsesExplicitAuthTypeAndRedactsLogging() throws Exception {
        var token = mapper.readValue("{\"authType\":\"TOKEN\",\"token\":\"fake-token\"}", Request.class);
        assertThat(token.authType()).isEqualTo(AuthenticationType.TOKEN);
        assertThat(token.token()).isEqualTo("fake-token");
        assertThat(token.username()).isNull();
        assertThat(token.password()).isNull();
        assertThat(token.toString()).doesNotContain("fake-token");
        assertThat(mapper.readValue("{\"authType\":\"USERNAME_PASSWORD\"}", Request.class).authType())
            .isEqualTo(AuthenticationType.USERNAME_PASSWORD);
    }

    @Test void unknownJsonAuthTypeIsRejected() {
        assertThatThrownBy(() -> mapper.readValue("{\"authType\":\"token\"}", Request.class))
            .hasMessageContaining("AuthenticationType");
    }

    @ParameterizedTest @ValueSource(strings = {"0", "1", "true", "[]", "{}"})
    void jsonAuthTypeMustBeAnExplicitEnumNameRatherThanACoercedValue(String value) {
        assertThatThrownBy(() -> mapper.readValue("{\"authType\":" + value + "}", Request.class))
            .hasMessageContaining("EXTERNAL_VECTOR_AUTH_TYPE_INVALID");
    }
}
