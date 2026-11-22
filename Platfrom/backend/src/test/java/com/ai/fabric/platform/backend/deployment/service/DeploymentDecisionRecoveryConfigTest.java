package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class DeploymentDecisionRecoveryConfigTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void explicitConfigurationRequiresActualRuntimeCapability() throws Exception {
        assertThat(DeploymentDecisionRecoveryConfig.requested(mapper.readTree("{}"))).isFalse();
        assertThat(DeploymentDecisionRecoveryConfig.requested(mapper.readTree("{\"decisionRecovery\":{\"maxRepairRounds\":3}}"))).isTrue();
        assertThatThrownBy(() -> DeploymentDecisionRecoveryConfig.requireSupport(mapper.readTree("{\"capabilities\":[\"ai-fabric-core\"]}")))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("compatible runtime");
        assertThatCode(() -> DeploymentDecisionRecoveryConfig.requireSupport(mapper.readTree("{\"capabilities\":[\"internal-decision-recovery-v1\"]}")))
            .doesNotThrowAnyException();
    }
}
