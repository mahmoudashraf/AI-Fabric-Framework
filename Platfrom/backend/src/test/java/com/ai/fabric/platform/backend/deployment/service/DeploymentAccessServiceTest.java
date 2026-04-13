package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

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
    void directServiceAccessRequiresPrincipal() {
        DeploymentAccessService service = service();

        assertThatThrownBy(() -> service.requireDeploymentAdminAccess(deployment()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode()).isEqualTo(NOT_FOUND));
    }

    private DeploymentAccessService service() {
        return new DeploymentAccessService(
            mock(DeploymentAssignmentRepository.class),
            mock(PublicApiDeploymentRepository.class),
            mock(PlatformUserRepository.class),
            mock(PlatformCustomerAccessService.class)
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
