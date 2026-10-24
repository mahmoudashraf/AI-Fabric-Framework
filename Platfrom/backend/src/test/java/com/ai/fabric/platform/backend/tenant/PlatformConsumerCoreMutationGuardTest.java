package com.ai.fabric.platform.backend.tenant;

import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceBindingGuard;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceDeploymentSecurityProjectionService;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedBindingBoundary;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.lifecycle.DeploymentTargetMutationService;
import com.ai.fabric.platform.backend.deployment.repository.*;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.*;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerBindingRequest;
import com.ai.fabric.platform.backend.tenant.repository.*;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlatformConsumerCoreMutationGuardTest {
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final PlatformConsumerBindingHistoryRepository history = mock(PlatformConsumerBindingHistoryRepository.class);
    private final PlatformCustomerRepository customers = mock(PlatformCustomerRepository.class);
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentTargetMutationService mutations = mock(DeploymentTargetMutationService.class);
    private final PlatformCustomerAccessService access = mock(PlatformCustomerAccessService.class);
    private final PlatformCustomerConsumerService service = new PlatformCustomerConsumerService(consumers, history,
        customers, deployments, releases, access, mock(PlatformAuditService.class), mock(AIWorkspaceBindingGuard.class),
        mock(AIWorkspaceInstallationRepository.class), mock(AIWorkspaceDeploymentSecurityProjectionService.class),
        new ObjectMapper().findAndRegisterModules(), mutations, mock(DeploymentTargetProfileRepository.class),
        mock(CustomerReviewedBindingBoundary.class));
    private final DeploymentEntity deployment = new DeploymentEntity();
    private final DeploymentReleaseEntity release = new DeploymentReleaseEntity();
    private final PlatformConsumerEntity consumer = new PlatformConsumerEntity();
    private final UpdatePlatformConsumerBindingRequest request = new UpdatePlatformConsumerBindingRequest(
        "deployment", "release", "target", "Activate verified release", 0L, "bind-command");

    @BeforeEach void setup() {
        var customer = new PlatformCustomerEntity(); customer.setId("customer"); customer.setStatus("ACTIVE");
        when(customers.findById("customer")).thenReturn(Optional.of(customer));
        consumer.setId("consumer"); consumer.setCustomerId("customer"); consumer.setConsumerId("web");
        consumer.setStatus("ACTIVE"); consumer.setBindingRevision(0);
        when(consumers.findBindingForUpdate("customer", "web")).thenReturn(Optional.of(consumer));
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        when(deployments.findById("deployment")).thenReturn(Optional.of(deployment));
        release.setId("release"); release.setDeploymentId("deployment"); release.setTargetProfileId("target");
        release.setStatus("APPLIED_VERIFIED"); release.setVerificationStatus("PASSED");
        when(releases.findById("release")).thenReturn(Optional.of(release));
    }

    @Test void exactCoreMutationGuardBlocksAssignmentBeforeAnyBindingOrHistoryWrite() {
        doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "RELEASE_APPLY_IN_PROGRESS"))
            .when(mutations).requireAvailableForMutation("deployment", "target");
        assertThatThrownBy(() -> service.updateBinding("customer", "web", request)).hasMessageContaining("RELEASE_APPLY_IN_PROGRESS");
        verify(access).requireCustomerManagementAccess("customer");
        verify(mutations).requireAvailableForMutation("deployment", "target");
        verify(consumers, never()).saveAndFlush(any());
        verify(history, never()).saveAndFlush(any());
        assertThat(consumer.getBindingRevision()).isZero();
    }

    @Test void unverifiedOrWrongTargetReleaseIsDeniedBeforeMutationGuard() {
        release.setVerificationStatus("FAILED");
        assertThatThrownBy(() -> service.updateBinding("customer", "web", request)).hasMessageContaining("verified releases");
        release.setVerificationStatus("PASSED"); release.setTargetProfileId("foreign-target");
        assertThatThrownBy(() -> service.updateBinding("customer", "web", request)).hasMessageContaining("target profile");
        verifyNoInteractions(mutations);
    }

    @Test void customerOwnershipCannotBeReplacedByAVerifiedRelease() {
        deployment.setCustomerId("foreign");
        assertThatThrownBy(() -> service.updateBinding("customer", "web", request)).hasMessageContaining("selected customer");
        verifyNoInteractions(mutations);
        verify(consumers, never()).saveAndFlush(any());
    }
}
