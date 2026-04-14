package com.ai.fabric.runtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void loadConfiguredFileExposesResolvedSources() {
        RuntimeDeploymentKnowledgeSourceConfigProperties properties = new RuntimeDeploymentKnowledgeSourceConfigProperties();
        properties.setConfigFile("classpath:test-runtime-knowledge-source-config.json");
        RuntimeDeploymentKnowledgeSourceConfigService service = new RuntimeDeploymentKnowledgeSourceConfigService(
            properties,
            new DefaultResourceLoader(),
            new ObjectMapper()
        );

        service.load();

        assertThat(service.currentContractVersion()).isEqualTo(RuntimeDeploymentKnowledgeSourceConfigService.CONTRACT_VERSION);
        assertThat(service.currentSourceIds()).containsExactly("shared-policies");
        assertThat(service.currentSourceTypes()).containsExactly("policy");
        assertThat(service.currentSourceAdapterTypes()).containsExactly("shared-index");
    }

    @Test
    void loadRejectsUnsupportedContractVersion() {
        RuntimeDeploymentKnowledgeSourceConfigProperties properties = new RuntimeDeploymentKnowledgeSourceConfigProperties();
        properties.setConfigFile("classpath:test-runtime-knowledge-source-config-invalid-contract.json");
        RuntimeDeploymentKnowledgeSourceConfigService service = new RuntimeDeploymentKnowledgeSourceConfigService(
            properties,
            new DefaultResourceLoader(),
            new ObjectMapper()
        );

        assertThatThrownBy(service::load)
            .isInstanceOf(IllegalStateException.class)
            .hasRootCauseMessage("Unsupported deployment knowledge source config contract version 'KNOWLEDGE_SOURCE_CONFIG_V99' in classpath:test-runtime-knowledge-source-config-invalid-contract.json. Supported version: KNOWLEDGE_SOURCE_CONFIG_V1");
    }
}
