package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessCheck;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessSummary;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AIWorkspaceBindingGuardTest {

    private final AIWorkspaceInstallationRepository repository = mock(AIWorkspaceInstallationRepository.class);
    private final AIWorkspaceReadinessService readiness = mock(AIWorkspaceReadinessService.class);
    private final AIWorkspaceBindingGuard guard = new AIWorkspaceBindingGuard(repository, readiness);

    @Test
    void blocksUnbindingConsumerWithActiveInstallation() {
        PlatformConsumerEntity consumer = consumer();
        when(repository.findByConsumerEntityIdAndStatusIn(consumer.getId(), List.of(AIWorkspaceInstallationStatus.ACTIVE)))
            .thenReturn(List.of(installation()));

        assertThatThrownBy(() -> guard.validateRebind(consumer, null, null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Disable active AI Workspace installations");
    }

    @Test
    void blocksIncompatibleTargetReleaseAndAllowsReadyReplacement() {
        PlatformConsumerEntity consumer = consumer();
        AIWorkspaceInstallationEntity installation = installation();
        when(repository.findByConsumerEntityIdAndStatusIn(consumer.getId(), List.of(AIWorkspaceInstallationStatus.ACTIVE)))
            .thenReturn(List.of(installation));
        when(readiness.evaluateAgainst(installation, consumer, "dep-2", "rel-2"))
            .thenReturn(new AIWorkspaceReadinessSummary(false, installation.getInstallationId(),
                AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS.manifestValue(), consumer.getConsumerId(),
                "dep-2", "rel-2", null, null,
                List.of(new AIWorkspaceReadinessCheck("RUNTIME_CORS", "BLOCKED", "Origin missing"))));

        assertThatThrownBy(() -> guard.validateRebind(consumer, "dep-2", "rel-2"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("RUNTIME_CORS");

        when(readiness.evaluateAgainst(installation, consumer, "dep-3", "rel-3"))
            .thenReturn(new AIWorkspaceReadinessSummary(true, installation.getInstallationId(),
                AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS.manifestValue(), consumer.getConsumerId(),
                "dep-3", "rel-3", "https://runtime.example", "sha256:" + "a".repeat(64), List.of()));
        guard.validateRebind(consumer, "dep-3", "rel-3");
        verify(readiness).evaluateAgainst(installation, consumer, "dep-3", "rel-3");
    }

    @Test
    void blocksConsumerDisableAndDeletionWhileWorkspaceLifecycleExists() {
        PlatformConsumerEntity consumer = consumer();
        when(repository.findByConsumerEntityIdAndStatusIn(
            consumer.getId(), List.of(AIWorkspaceInstallationStatus.ACTIVE)))
            .thenReturn(List.of(installation()));

        assertThatThrownBy(() -> guard.validateConsumerStatusChange(consumer, "DISABLED"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Disable active AI Workspace installations");

        when(repository.existsByConsumerEntityId(consumer.getId())).thenReturn(true);
        assertThatThrownBy(() -> guard.validateConsumerDeletion(consumer))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("installation history");
    }

    private PlatformConsumerEntity consumer() {
        PlatformConsumerEntity consumer = new PlatformConsumerEntity();
        consumer.setId("consumer-entity-1");
        consumer.setConsumerId("consumer-public-1");
        consumer.setCustomerId("customer-1");
        consumer.setStatus("ACTIVE");
        return consumer;
    }

    private AIWorkspaceInstallationEntity installation() {
        AIWorkspaceInstallationEntity installation = new AIWorkspaceInstallationEntity();
        installation.setId("awi-1");
        installation.setInstallationId("awi_pub_0123456789abcdef0123456789abcdef");
        installation.setStatus(AIWorkspaceInstallationStatus.ACTIVE);
        installation.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS);
        return installation;
    }
}
