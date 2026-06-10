package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentCuratedModuleCatalogServiceTest {

    @Test
    void defaultModuleUsesDefaultRuntimeCuratedPack() {
        DeploymentCuratedModuleCatalogService service = new DeploymentCuratedModuleCatalogService(
            new ObjectMapper(),
            new DefaultResourceLoader()
        );

        assertThat(service.normalizeModuleId(null)).isEqualTo("default");
        assertThat(service.runtimeCuratedPack(null)).isEqualTo("default");
        assertThat(service.resolveSummary(" default ").runtimeCuratedPack()).isEqualTo("default");
    }
}
