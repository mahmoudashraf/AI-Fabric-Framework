package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformAuthProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentAssignmentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentWorkspaceAccessSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentAccessService {

    private final DeploymentAssignmentRepository deploymentAssignmentRepository;
    private final PublicApiDeploymentRepository publicApiDeploymentRepository;
    private final PlatformUserRepository platformUserRepository;
    private final PlatformCustomerAccessService platformCustomerAccessService;
    private final PlatformAuthProperties authProperties;

    public DeploymentAccessService(DeploymentAssignmentRepository deploymentAssignmentRepository,
                                   PublicApiDeploymentRepository publicApiDeploymentRepository,
                                   PlatformUserRepository platformUserRepository,
                                   PlatformCustomerAccessService platformCustomerAccessService,
                                   PlatformAuthProperties authProperties) {
        this.deploymentAssignmentRepository = deploymentAssignmentRepository;
        this.publicApiDeploymentRepository = publicApiDeploymentRepository;
        this.platformUserRepository = platformUserRepository;
        this.platformCustomerAccessService = platformCustomerAccessService;
        this.authProperties = authProperties;
    }

    public List<DeploymentEntity> filterVisibleDeployments(List<DeploymentEntity> deployments) {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (globalPlatformDeploymentRole(principal) != null) {
            return deployments;
        }
        if (principal == null) {
            return List.of();
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

        if (principal.role() == PlatformRole.CUSTOMER_ADMIN) {
            PlatformCustomerAccessService.CustomerScopeSummary scope = platformCustomerAccessService.currentScope();
            if (scope == null) {
                return List.of();
            }
            return deployments.stream()
                .filter(deployment -> scope.customerId().equals(deployment.getCustomerId()))
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
        return requireDeploymentRole(deployment, "DEPLOYMENT_VIEWER");
    }

    public DeploymentEntity requireDeploymentOperatorAccess(DeploymentEntity deployment) {
        return requireDeploymentRole(deployment, "DEPLOYMENT_OPERATOR");
    }

    public DeploymentEntity requireDeploymentEditorAccess(DeploymentEntity deployment) {
        return requireDeploymentRole(deployment, "DEPLOYMENT_EDITOR");
    }

    public DeploymentEntity requireDeploymentAdminAccess(DeploymentEntity deployment) {
        return requireDeploymentRole(deployment, "DEPLOYMENT_ADMIN");
    }

    private DeploymentEntity requireDeploymentRole(DeploymentEntity deployment, String minimumAssignmentRole) {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        String globalRole = globalPlatformDeploymentRole(principal);
        if (globalRole != null) {
            if (assignmentRoleRank(globalRole) < assignmentRoleRank(minimumAssignmentRole)) {
                throw new ResponseStatusException(
                    FORBIDDEN,
                    "Platform role " + globalRole + " is insufficient. Required: " + minimumAssignmentRole + " or higher."
                );
            }
            return deployment;
        }
        if (principal == null) {
            throw new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deployment.getId());
        }

        if (principal.role() == PlatformRole.PUBLIC_API_CLIENT) {
            boolean clientOwnsDeployment = publicApiDeploymentRepository
                .findByClientIdAndDeploymentId(principal.actorId(), deployment.getId())
                .isPresent();
            if (!clientOwnsDeployment) {
                throw new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deployment.getId());
            }
            if (assignmentRoleRank(minimumAssignmentRole) > assignmentRoleRank("DEPLOYMENT_VIEWER")) {
                throw new ResponseStatusException(FORBIDDEN, "This deployment action is not available for public API clients.");
            }
            return deployment;
        }

        if (principal.role() == PlatformRole.CUSTOMER_ADMIN) {
            try {
                platformCustomerAccessService.requireDeploymentCustomerAccess(deployment.getCustomerId());
                return deployment;
            } catch (ResponseStatusException ex) {
                throw new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deployment.getId());
            }
        }

        String currentUserId = currentUserId(principal);
        if (currentUserId == null) {
            throw new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deployment.getId());
        }

        DeploymentAssignmentEntity assignment = deploymentAssignmentRepository.findByUserIdAndDeploymentId(currentUserId, deployment.getId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deployment.getId()));

        if (assignmentRoleRank(assignment.getAssignmentRole()) < assignmentRoleRank(minimumAssignmentRole)) {
            throw new ResponseStatusException(
                FORBIDDEN,
                "Deployment role " + assignment.getAssignmentRole() + " is insufficient. Required: " + minimumAssignmentRole + " or higher."
            );
        }
        return deployment;
    }

    public String currentUserIdOrNull() {
        return currentUserId(PlatformSecurityContext.currentPrincipal());
    }

    public DeploymentWorkspaceAccessSummary summarizeAccess(DeploymentEntity deployment) {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        String globalRole = globalPlatformDeploymentRole(principal);
        if (globalRole != null) {
            int rank = assignmentRoleRank(globalRole);
            return new DeploymentWorkspaceAccessSummary(
                globalRole,
                rank >= assignmentRoleRank("DEPLOYMENT_OPERATOR"),
                rank >= assignmentRoleRank("DEPLOYMENT_EDITOR"),
                rank >= assignmentRoleRank("DEPLOYMENT_ADMIN")
            );
        }
        if (principal == null) {
            return new DeploymentWorkspaceAccessSummary("NONE", false, false, false);
        }

        if (principal.role() == PlatformRole.PUBLIC_API_CLIENT) {
            boolean allowed = publicApiDeploymentRepository
                .findByClientIdAndDeploymentId(principal.actorId(), deployment.getId())
                .isPresent();
            if (!allowed) {
                return new DeploymentWorkspaceAccessSummary("NONE", false, false, false);
            }
            return new DeploymentWorkspaceAccessSummary("DEPLOYMENT_VIEWER", false, false, false);
        }

        if (principal.role() == PlatformRole.CUSTOMER_ADMIN) {
            try {
                platformCustomerAccessService.requireDeploymentCustomerAccess(deployment.getCustomerId());
                return new DeploymentWorkspaceAccessSummary("DEPLOYMENT_ADMIN", true, true, true);
            } catch (ResponseStatusException ex) {
                return new DeploymentWorkspaceAccessSummary("NONE", false, false, false);
            }
        }

        String currentUserId = currentUserId(principal);
        if (currentUserId == null) {
            return new DeploymentWorkspaceAccessSummary("NONE", false, false, false);
        }

        DeploymentAssignmentEntity assignment = deploymentAssignmentRepository
            .findByUserIdAndDeploymentId(currentUserId, deployment.getId())
            .orElse(null);
        if (assignment == null) {
            return new DeploymentWorkspaceAccessSummary("NONE", false, false, false);
        }

        String assignmentRole = assignment.getAssignmentRole();
        int rank = assignmentRoleRank(assignmentRole);
        return new DeploymentWorkspaceAccessSummary(
            assignmentRole,
            rank >= assignmentRoleRank("DEPLOYMENT_OPERATOR"),
            rank >= assignmentRoleRank("DEPLOYMENT_EDITOR"),
            rank >= assignmentRoleRank("DEPLOYMENT_ADMIN")
        );
    }

    private String globalPlatformDeploymentRole(PlatformPrincipal principal) {
        if (principal == null) {
            if (!authProperties.enabled()) {
                return "DEPLOYMENT_ADMIN";
            }
            return null;
        }
        if (principal.role() == PlatformRole.PLATFORM_ADMIN) {
            return "DEPLOYMENT_ADMIN";
        }
        if (!"API_KEY".equalsIgnoreCase(principal.authenticationMode())) {
            return null;
        }
        return switch (principal.role()) {
            case PLATFORM_ADMIN -> "DEPLOYMENT_ADMIN";
            case PLATFORM_OPERATOR -> "DEPLOYMENT_EDITOR";
            default -> null;
        };
    }

    private String currentUserId(PlatformPrincipal principal) {
        if (principal == null) {
            return null;
        }
        PlatformUserEntity user = platformUserRepository.findByEmailIgnoreCase(principal.actorId()).orElse(null);
        return user == null ? null : user.getId();
    }

    private int assignmentRoleRank(String assignmentRole) {
        return switch (assignmentRole == null ? "" : assignmentRole.trim().toUpperCase()) {
            case "DEPLOYMENT_ADMIN" -> 4;
            case "DEPLOYMENT_EDITOR" -> 3;
            case "DEPLOYMENT_OPERATOR" -> 2;
            case "DEPLOYMENT_VIEWER" -> 1;
            default -> 0;
        };
    }
}
