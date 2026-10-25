package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.web.DeploymentTargetOptionsController;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeploymentTargetOptionsControllerTest {
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentTargetProfileRepository profiles = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentAccessService access = mock(DeploymentAccessService.class);
    private final CustomerSelfServiceOperationBoundary authorization = mock(CustomerSelfServiceOperationBoundary.class);
    private final DeploymentTargetOptionsController controller = new DeploymentTargetOptionsController(deployments, profiles, access, authorization);

    @Test void ownedDeploymentListsOnlyAuthorizedTargetsWithoutInfrastructureConfiguration() {
        var deployment = deployment();
        var allowed = profile("production");
        allowed.setProviderConfigJson("{\"serverUuid\":\"private-server\"}");
        allowed.setCredentialRefId("private-credential");
        var denied = profile("foreign");
        var inactive = profile("inactive"); inactive.setActive(false);
        when(profiles.findAll()).thenReturn(List.of(allowed, denied, inactive));
        when(authorization.require(eq(deployment), eq("foreign"), isNull(), any(), isNull(), anyMap(), isNull(), isNull(), isNull()))
            .thenThrow(CustomerSelfServiceException.conflict(PLACEMENT_DENIED));
        var result = controller.list(deployment.getId());
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo("production");
        assertThat(result.getFirst().credentialRefId()).isNull();
        assertThat(result.getFirst().providerConfig()).isEmpty();
        assertThat(result.getFirst().networkPolicy()).isEmpty();
        assertThat(result.getFirst().resourceDefaults()).isEmpty();
        verify(authorization, never()).require(eq(deployment), eq("inactive"), any(), any(), any(), any(), any(), any(), any());
    }

    @Test void foreignDeploymentIsRejectedBeforeTargetEnumeration() {
        var deployment = deployment();
        when(access.requireDeploymentAccess(deployment)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> controller.list(deployment.getId())).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(profiles, authorization);
    }

    @Test void accountOrTenantDenialIsNotHiddenAsAnEmptyTargetList() {
        var deployment = deployment();
        when(profiles.findAll()).thenReturn(List.of(profile("production")));
        when(authorization.require(eq(deployment), anyString(), isNull(), any(), isNull(), anyMap(), isNull(), isNull(), isNull()))
            .thenThrow(CustomerSelfServiceException.deny(RESOURCE_SCOPE_MISMATCH));
        assertThatThrownBy(() -> controller.list(deployment.getId())).hasMessage(RESOURCE_SCOPE_MISMATCH.name());
    }

    private DeploymentEntity deployment() {
        var value = new DeploymentEntity(); value.setId("owned");
        when(deployments.findById(value.getId())).thenReturn(Optional.of(value));
        return value;
    }

    private DeploymentTargetProfileEntity profile(String id) {
        var value = new DeploymentTargetProfileEntity(); value.setId(id); value.setName(id); value.setActive(true);
        return value;
    }
}
