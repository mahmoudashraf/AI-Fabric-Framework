package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.security.core.context.SecurityContextHolder.clearContext;

class DeploymentAccessServiceTest {

    @AfterEach
    void tearDown() {
        clearContext();
    }

    @Test
    void authDisabledModeAllowsDirectServiceAccessWithoutPrincipal() {
        DeploymentAccessService service = serviceWithAuthEnabled(false);

        DeploymentEntity deployment = deployment();

        assertThat(service.requireDeploymentAdminAccess(deployment)).isSameAs(deployment);
        assertThat(service.requireDeploymentEditorAccess(deployment)).isSameAs(deployment);
    }

    @Test
    void authEnabledModeRequiresPrincipalForDirectServiceAccess() {
        DeploymentAccessService service = serviceWithAuthEnabled(true);

        assertThatThrownBy(() -> service.requireDeploymentAdminAccess(deployment()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(NOT_FOUND));
    }

    private DeploymentAccessService serviceWithAuthEnabled(boolean authEnabled) {
        return new DeploymentAccessService(
            mock(DeploymentAssignmentRepository.class),
            mock(PublicApiDeploymentRepository.class),
            mock(PlatformUserRepository.class),
            mock(PlatformCustomerAccessService.class),
            new PlatformAuthProperties(
                authEnabled,
                "X-PLATFORM-API-KEY",
                true,
                true,
                "platform_session",
                Duration.ofHours(12),
                false,
                "Lax",
                "operator-key",
                "admin-key",
                true,
                "admin@example.com",
                "AdminPass123!",
                "Admin"
            )
        );
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setCustomerId("cus-123");
        deployment.setTenantId("ten-123");
        return deployment;
    }
}
