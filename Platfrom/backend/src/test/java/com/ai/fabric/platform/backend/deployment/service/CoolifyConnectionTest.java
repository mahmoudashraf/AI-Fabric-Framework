package com.ai.fabric.platform.backend.deployment.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CoolifyConnectionTest {

    @Test
    void toStringMasksBearerToken() {
        CoolifyConnection connection = new CoolifyConnection(
            "https://coolify.example",
            "secret-token",
            new CoolifyTargetProfileConfig(
                "https://coolify.example",
                "project",
                "production",
                "env",
                "server",
                "destination",
                "example.com",
                "v1",
                5,
                300,
                true,
                true,
                "8080",
                "/actuator/health",
                "8080"
            )
        );

        assertThat(connection.toString()).contains("token=***");
        assertThat(connection.toString()).doesNotContain("secret-token");
    }
}
