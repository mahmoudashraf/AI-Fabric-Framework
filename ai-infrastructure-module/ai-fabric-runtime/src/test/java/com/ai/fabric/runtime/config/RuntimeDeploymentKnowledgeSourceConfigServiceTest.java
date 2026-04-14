package com.ai.fabric.runtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeDeploymentKnowledgeSourceConfigServiceTest {

    @Test
    void loadWithoutConfiguredFileKeepsDefaults() {
        RuntimeDeploymentKnowledgeSourceConfigProperties properties = new RuntimeDeploymentKnowledgeSourceConfigProperties();
        RuntimeDeploymentKnowledgeSourceConfigService service = new RuntimeDeploymentKnowledgeSourceConfigService(
            properties,
            new DefaultResourceLoader(),
            new ObjectMapper()
        );

        service.load();

        assertThat(service.currentContractVersion()).isEqualTo(RuntimeDeploymentKnowledgeSourceConfigService.CONTRACT_VERSION);
        assertThat(service.currentSourceIds()).isEmpty();
        assertThat(service.currentSourceTypes()).isEmpty();
        assertThat(service.currentSourceAdapterTypes()).isEmpty();
        assertThat(service.currentSources()).isEmpty();
        assertThat(service.currentRoot().path("sources").isArray()).isTrue();
    }
}
