package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentAssignmentEntity;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
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

    @Test void revokedWorkspaceGrantOverridesLegacyAssignmentWithoutLoggingStaffOut() {
        var customers = mock(PlatformCustomerAccessService.class);
        var assignments = mock(DeploymentAssignmentRepository.class);
        var users = mock(PlatformUserRepository.class);
        var operator = new PlatformPrincipal("engineer@example.test", PlatformRole.PLATFORM_OPERATOR, "Engineer", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(operator, null, List.of()));
        var user = new PlatformUserEntity(); user.setId("staff-1");
        when(users.findByEmailIgnoreCase(operator.actorId())).thenReturn(Optional.of(user));
        var assignment = new DeploymentAssignmentEntity(); assignment.setAssignmentRole("DEPLOYMENT_ADMIN");
        when(assignments.findByUserIdAndDeploymentId("staff-1", "dep-123")).thenReturn(Optional.of(assignment));
        var service = new DeploymentAccessService(assignments, mock(PublicApiDeploymentRepository.class), users, customers);
        var deployment = deployment();

        when(customers.staffCustomerAccessDecision("cus-123")).thenReturn(Optional.of(true));
        assertThat(service.filterVisibleDeployments(List.of(deployment))).containsExactly(deployment);
        assertThat(service.requireDeploymentAdminAccess(deployment)).isSameAs(deployment);
        assertThat(service.summarizeAccess(deployment).canEdit()).isTrue();

        when(customers.staffCustomerAccessDecision("cus-123")).thenReturn(Optional.of(false));
        assertThat(service.filterVisibleDeployments(List.of(deployment))).isEmpty();
        assertThat(service.summarizeAccess(deployment).canEdit()).isFalse();
        assertThatThrownBy(() -> service.requireDeploymentAccess(deployment)).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(assignments);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal()).isSameAs(operator);

        when(customers.staffCustomerAccessDecision("cus-123")).thenReturn(Optional.empty());
        assertThat(service.requireDeploymentAdminAccess(deployment)).isSameAs(deployment);
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
