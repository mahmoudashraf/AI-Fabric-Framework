package com.ai.fabric.platform.backend.deployment.retirement;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.*;
import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TargetRetirementOfferAuthorizationTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void unentitledRetirementCannotCreateAPlanOrReachAProviderParticipant() {
        var principal = new PlatformPrincipal("owner@test.invalid", PlatformRole.CUSTOMER_ADMIN, "Owner", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority(PlatformRole.CUSTOMER_ADMIN.authority()))));
        var deployments = mock(DeploymentRepository.class);
        var deployment = new DeploymentEntity(); deployment.setId("dep-a");
        when(deployments.findById("dep-a")).thenReturn(Optional.of(deployment));
        var store = mock(TargetRetirementStore.class);
        var participants = mock(TargetRetirementParticipation.class);
        var boundary = mock(CustomerSelfServiceOperationBoundary.class);
        doThrow(deny(OPERATION_FORBIDDEN)).when(boundary).require(eq(deployment), eq("staging"), eq("release"),
            eq(Operation.TARGET_RETIRE), eq("retire-key"), anyMap(), isNull(), isNull(), isNull());
        var service = new TargetRetirementService(deployments, mock(DeploymentAccessService.class),
            mock(TargetRetirementOperationRepository.class), store, participants, boundary);
        var request = new PlanRequest("retire-key", List.of("release"), List.of(), Instant.now().plusSeconds(60),
            new Retention(RetentionDecision.RECLAIM, null, null, "approved-source-rebuild"), "Owned target cleanup");
        assertThatThrownBy(() -> service.plan("dep-a", "staging", request)).hasMessageContaining("OPERATION_FORBIDDEN");
        verifyNoInteractions(store, participants);
    }
}
