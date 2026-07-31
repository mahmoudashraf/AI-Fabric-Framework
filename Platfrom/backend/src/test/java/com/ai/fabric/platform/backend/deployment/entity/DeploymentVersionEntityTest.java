package com.ai.fabric.platform.backend.deployment.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeploymentVersionEntityTest {

    @Test
    void newVersionsDefaultToCurrentFrameworkAndEntityContract() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();

        assertThat(version.getAiFabricFrameworkVersion()).isEqualTo("0.5.2");
        assertThat(version.getEntityConfigContractVersion()).isEqualTo("AI_ENTITY_CONFIG_V0_4");
    }
}
