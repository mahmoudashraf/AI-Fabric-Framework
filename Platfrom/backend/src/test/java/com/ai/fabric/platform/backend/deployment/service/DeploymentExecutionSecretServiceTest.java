package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DeploymentExecutionSecretServiceTest {

    private final DeploymentExecutionSecretService service = new DeploymentExecutionSecretService(
        mock(PlatformSecretService.class),
        new ObjectMapper()
    );

    @Test
    void agenticDeploymentReceivesOnlySpecialistChainSecrets() {
        assertThat(service.requiredSecretNames(deployment(), version("""
            {"type":"AGENTIC_SPECIALIST_TEAM","executionExtensions":[]}
            """))).containsExactlyInAnyOrder(
            DeploymentExecutionSecretService.chainEncryptionSecretName("dep-1"),
            DeploymentExecutionSecretService.chainFingerprintSecretName("dep-1")
        );
    }

    @Test
    void smartBrainDeploymentReceivesJobOperationAndDeliverySecretsWithoutChainSecrets() {
        Set<String> names = service.requiredSecretNames(deployment(), version("""
            {"type":"SMART_BRAIN","executionExtensions":[]}
            """));

        assertThat(names).containsExactlyInAnyOrder(
            DeploymentExecutionSecretService.smartBrainJobEncryptionSecretName("dep-1"),
            DeploymentExecutionSecretService.smartBrainJobFingerprintSecretName("dep-1"),
            DeploymentExecutionSecretService.smartBrainEncryptionSecretName("dep-1"),
            DeploymentExecutionSecretService.smartBrainFingerprintSecretName("dep-1"),
            DeploymentExecutionSecretService.smartBrainDeliverySigningSecretName("dep-1")
        );
        assertThat(names).doesNotContain(
            DeploymentExecutionSecretService.chainEncryptionSecretName("dep-1"),
            DeploymentExecutionSecretService.chainFingerprintSecretName("dep-1")
        );
    }

    @Test
    void humanReviewAddsItsReceiptAndReviewSecretsToACompatibleBehavior() {
        assertThat(service.requiredSecretNames(deployment(), version("""
            {"type":"CONVERSATIONAL","executionExtensions":["HUMAN_REVIEW"]}
            """))).containsExactlyInAnyOrder(
            DeploymentExecutionSecretService.actionReceiptEncryptionSecretName("dep-1"),
            DeploymentExecutionSecretService.actionReceiptFingerprintSecretName("dep-1"),
            DeploymentExecutionSecretService.reviewEncryptionSecretName("dep-1"),
            DeploymentExecutionSecretService.reviewFingerprintSecretName("dep-1")
        );
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setBehaviorType("CONVERSATIONAL");
        return deployment;
    }

    private DeploymentVersionEntity version(String behaviorConfig) {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setBehaviorConfigJson(behaviorConfig);
        return version;
    }
}
