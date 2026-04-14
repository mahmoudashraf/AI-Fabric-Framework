package com.ai.fabric.runtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDeploymentShellConfigServiceTest {

    @Test
    void loadWithoutConfiguredFileKeepsDefaults() {
        RuntimeDeploymentShellConfigProperties properties = new RuntimeDeploymentShellConfigProperties();
        RuntimeDeploymentShellConfigService service = new RuntimeDeploymentShellConfigService(
            properties,
            new DefaultResourceLoader(),
            new ObjectMapper()
        );

        service.load();

        assertThat(service.currentContractVersion()).isEqualTo(RuntimeDeploymentShellConfigService.CONTRACT_VERSION);
        assertThat(service.currentModuleIds()).isEmpty();
        assertThat(service.currentCardIds()).isEmpty();
        assertThat(service.currentRoot().path("modules").isArray()).isTrue();
        assertThat(service.currentRoot().path("cards").isArray()).isTrue();
    }
}
