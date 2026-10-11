package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformVectorizationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerRegistrationEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorizationRunnerProvisioningServiceTest {

    private final PlatformAuditService auditService = mock(PlatformAuditService.class);
    private final PlatformSecretService secretService = mock(PlatformSecretService.class);
    private final VectorizationRunnerRegistrationRepository registrationRepository =
        mock(VectorizationRunnerRegistrationRepository.class);
    private final VectorizationTokenService tokenService = new VectorizationTokenService();
    private final PlatformVectorizationProperties properties = new PlatformVectorizationProperties(
        Duration.ofDays(7),
        Duration.ofDays(365),
        Duration.ofDays(30),
        Duration.ofHours(6),
        Duration.ofMinutes(15),
        20,
        "2026.04.track-b",
        "1"
    );

    @Test
    void keepsManagedRegistrationOutsideRenewalWindow() {
        DeploymentEntity deployment = deployment();
        String secretName = VectorizationManagedSecretNames.registrationTokenSecretName(deployment.getId());
        String token = "managed-runner-token";
        VectorizationRunnerRegistrationEntity registration = registration(
            deployment,
            token,
            Instant.now().plus(Duration.ofDays(60))
        );
        Instant originalExpiry = registration.getTokenExpiresAt();
        when(secretService.resolveSecret(secretName)).thenReturn(token);
        when(registrationRepository.findByDeploymentId(deployment.getId())).thenReturn(Optional.of(registration));

        VectorizationRunnerProvisioningService.RunnerProvisioningMaterial material = service()
            .ensureManagedRegistration(deployment);

        assertThat(material.tokenExpiresAt()).isEqualTo(originalExpiry);
        assertThat(registration.getTokenHash()).isEqualTo(tokenService.hashToken(token));
        verify(secretService, never()).upsertManagedSecret(any(), any(), anyMap());
    }

    @Test
    void rotatesManagedRegistrationInsideRenewalWindow() {
        DeploymentEntity deployment = deployment();
        String secretName = VectorizationManagedSecretNames.registrationTokenSecretName(deployment.getId());
        String oldToken = "managed-runner-token";
        VectorizationRunnerRegistrationEntity registration = registration(
            deployment,
            oldToken,
            Instant.now().plus(Duration.ofDays(10))
        );
        when(secretService.resolveSecret(secretName)).thenReturn(oldToken);
        when(registrationRepository.findByDeploymentId(deployment.getId())).thenReturn(Optional.of(registration));

        VectorizationRunnerProvisioningService.RunnerProvisioningMaterial material = service()
            .ensureManagedRegistration(deployment);

        ArgumentCaptor<String> tokenCaptor = ArgumentCaptor.forClass(String.class);
        verify(secretService).upsertManagedSecret(eq(secretName), tokenCaptor.capture(), anyMap());
        assertThat(tokenCaptor.getValue()).isNotBlank().isNotEqualTo(oldToken);
        assertThat(registration.getTokenHash()).isEqualTo(tokenService.hashToken(tokenCaptor.getValue()));
        assertThat(material.tokenExpiresAt()).isAfter(Instant.now().plus(Duration.ofDays(364)));
    }

    private VectorizationRunnerProvisioningService service() {
        return new VectorizationRunnerProvisioningService(
            properties,
            auditService,
            secretService,
            registrationRepository,
            tokenService
        );
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setCustomerId("cus-123");
        deployment.setTenantId("tenant-123");
        return deployment;
    }

    private VectorizationRunnerRegistrationEntity registration(
        DeploymentEntity deployment,
        String token,
        Instant expiresAt
    ) {
        VectorizationRunnerRegistrationEntity registration = new VectorizationRunnerRegistrationEntity();
        registration.setId("vrr-123");
        registration.setDeploymentId(deployment.getId());
        registration.setCustomerId(deployment.getCustomerId());
        registration.setTenantId(deployment.getTenantId());
        registration.setRunnerMode("PLATFORM_MANAGED_AUTO");
        registration.setStatus("ACTIVE");
        registration.setTokenHash(tokenService.hashToken(token));
        registration.setTokenHint(tokenService.tokenHint(token));
        registration.setTokenExpiresAt(expiresAt);
        registration.setCreatedAt(Instant.now().minus(Duration.ofDays(1)));
        registration.setUpdatedAt(Instant.now().minus(Duration.ofDays(1)));
        return registration;
    }
}
