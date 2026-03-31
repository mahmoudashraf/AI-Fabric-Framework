package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentAssignmentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentAccessService {

    private final DeploymentAssignmentRepository deploymentAssignmentRepository;
    private final PublicApiDeploymentRepository publicApiDeploymentRepository;
    private final PlatformUserRepository platformUserRepository;

    public DeploymentAccessService(DeploymentAssignmentRepository deploymentAssignmentRepository,
                                   PublicApiDeploymentRepository publicApiDeploymentRepository,
                                   PlatformUserRepository platformUserRepository) {
        this.deploymentAssignmentRepository = deploymentAssignmentRepository;
        this.publicApiDeploymentRepository = publicApiDeploymentRepository;
        this.platformUserRepository = platformUserRepository;
    }

    public List<DeploymentEntity> filterVisibleDeployments(List<DeploymentEntity> deployments) {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() == PlatformRole.PLATFORM_ADMIN || hasGlobalPlatformAccess(principal)) {
            return deployments;
        }

        if (principal.role() == PlatformRole.PUBLIC_API_CLIENT) {
            Set<String> allowedDeploymentIds = publicApiDeploymentRepository.findByClientId(principal.actorId())
                .stream()
                .map(binding -> binding.getDeploymentId())
                .collect(java.util.stream.Collectors.toSet());
            return deployments.stream()
                .filter(deployment -> allowedDeploymentIds.contains(deployment.getId()))
                .toList();
        }

        String currentUserId = currentUserId(principal);
        if (currentUserId == null) {
            return List.of();
        }

        Set<String> allowedDeploymentIds = deploymentAssignmentRepository.findByUserIdOrderByCreatedAtAsc(currentUserId)
            .stream()
            .map(DeploymentAssignmentEntity::getDeploymentId)
            .collect(java.util.stream.Collectors.toSet());

        return deployments.stream()
            .filter(deployment -> allowedDeploymentIds.contains(deployment.getId()))
            .toList();
    }

    public DeploymentEntity requireDeploymentAccess(DeploymentEntity deployment) {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() == PlatformRole.PLATFORM_ADMIN || hasGlobalPlatformAccess(principal)) {
            return deployment;
        }

        if (principal.role() == PlatformRole.PUBLIC_API_CLIENT) {
            boolean clientOwnsDeployment = publicApiDeploymentRepository
                .findByClientIdAndDeploymentId(principal.actorId(), deployment.getId())
                .isPresent();
            if (!clientOwnsDeployment) {
                throw new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deployment.getId());
            }
            return deployment;
        }

        String currentUserId = currentUserId(principal);
        boolean allowed = currentUserId != null
            && deploymentAssignmentRepository.findByUserIdAndDeploymentId(currentUserId, deployment.getId()).isPresent();
        if (!allowed) {
            throw new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deployment.getId());
        }
        return deployment;
    }

    public String currentUserIdOrNull() {
        return currentUserId(PlatformSecurityContext.currentPrincipal());
    }

    private boolean hasGlobalPlatformAccess(PlatformPrincipal principal) {
        return principal != null
            && "API_KEY".equalsIgnoreCase(principal.authenticationMode())
            && principal.role() != PlatformRole.PUBLIC_API_CLIENT;
    }

    private String currentUserId(PlatformPrincipal principal) {
        if (principal == null) {
            return null;
        }
        PlatformUserEntity user = platformUserRepository.findByEmailIgnoreCase(principal.actorId()).orElse(null);
        return user == null ? null : user.getId();
    }
}
