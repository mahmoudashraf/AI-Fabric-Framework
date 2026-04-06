package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformVectorizationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerRegistrationEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class VectorizationRunnerProvisioningService {

    private final PlatformVectorizationProperties properties;
    private final PlatformAuditService platformAuditService;
    private final PlatformSecretService platformSecretService;
    private final VectorizationRunnerRegistrationRepository registrationRepository;
    private final VectorizationTokenService tokenService;

    public VectorizationRunnerProvisioningService(PlatformVectorizationProperties properties,
                                                  PlatformAuditService platformAuditService,
                                                  PlatformSecretService platformSecretService,
                                                  VectorizationRunnerRegistrationRepository registrationRepository,
                                                  VectorizationTokenService tokenService) {
        this.properties = properties;
        this.platformAuditService = platformAuditService;
        this.platformSecretService = platformSecretService;
        this.registrationRepository = registrationRepository;
        this.tokenService = tokenService;
    }

    @Transactional
    public RunnerProvisioningMaterial ensureManagedRegistration(DeploymentEntity deployment) {
        String secretName = VectorizationManagedSecretNames.registrationTokenSecretName(deployment.getId());
        String existingToken = platformSecretService.resolveSecret(secretName);
        VectorizationRunnerRegistrationEntity registration = registrationRepository.findByDeploymentId(deployment.getId()).orElse(null);
        Instant now = Instant.now();

        if (registration != null
            && "ACTIVE".equalsIgnoreCase(registration.getStatus())
            && registration.getTokenExpiresAt() != null
            && registration.getTokenExpiresAt().isAfter(now)
            && StringUtils.hasText(existingToken)
            && tokenService.hashToken(existingToken).equals(registration.getTokenHash())) {
            registration.setRunnerMode("PLATFORM_MANAGED_AUTO");
            registration.setUpdatedAt(now);
            registrationRepository.save(registration);
            return new RunnerProvisioningMaterial(registration.getId(), secretName, registration.getTokenExpiresAt());
        }

        String registrationToken = tokenService.generateToken();
        Instant expiresAt = now.plus(properties.registrationTokenTtl());
        VectorizationRunnerRegistrationEntity entity = registration != null ? registration : new VectorizationRunnerRegistrationEntity();
        if (!StringUtils.hasText(entity.getId())) {
            entity.setId(generateId("vrr"));
            entity.setDeploymentId(deployment.getId());
            entity.setCustomerId(deployment.getCustomerId());
            entity.setTenantId(deployment.getTenantId());
            entity.setCreatedAt(now);
        }
        entity.setRunnerMode("PLATFORM_MANAGED_AUTO");
        entity.setStatus("ACTIVE");
        entity.setTokenHash(tokenService.hashToken(registrationToken));
        entity.setTokenHint(tokenService.tokenHint(registrationToken));
        entity.setTokenExpiresAt(expiresAt);
        entity.setUpdatedAt(now);
        registrationRepository.save(entity);

        platformSecretService.upsertManagedSecret(
            secretName,
            registrationToken,
            Map.of(
                "deploymentId", deployment.getId(),
                "registrationId", entity.getId(),
                "purpose", "VECTORIZATION_RUNNER_REGISTRATION"
            )
        );
        platformAuditService.record(
            "VECTORIZATION_RUNNER_REGISTRATION_PROVISIONED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "deploymentId", deployment.getId(),
                "registrationId", entity.getId(),
                "secretName", secretName
            )
        );

        return new RunnerProvisioningMaterial(entity.getId(), secretName, expiresAt);
    }

    @Transactional
    public void clearManagedRegistrationSecret(String deploymentId) {
        String secretName = VectorizationManagedSecretNames.registrationTokenSecretName(deploymentId);
        if (!platformSecretService.isManagedSecretName(secretName) || !platformSecretService.isSecretPresent(secretName)) {
            return;
        }
        platformSecretService.clearManagedSecret(
            secretName,
            Map.of(
                "deploymentId", deploymentId,
                "purpose", "VECTORIZATION_RUNNER_REGISTRATION"
            )
        );
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    public record RunnerProvisioningMaterial(
        String registrationId,
        String secretName,
        Instant tokenExpiresAt
    ) {
    }
}
