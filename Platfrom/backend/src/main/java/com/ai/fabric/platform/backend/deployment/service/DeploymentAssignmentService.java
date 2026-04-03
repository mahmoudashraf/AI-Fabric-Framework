package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentAssignmentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentAssignmentSummary;
import com.ai.fabric.platform.backend.deployment.model.UpsertDeploymentAssignmentRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentAssignmentService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentAssignmentRepository deploymentAssignmentRepository;
    private final PlatformUserRepository platformUserRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final PlatformAuditService platformAuditService;

    public DeploymentAssignmentService(DeploymentRepository deploymentRepository,
                                       DeploymentAssignmentRepository deploymentAssignmentRepository,
                                       PlatformUserRepository platformUserRepository,
                                       DeploymentAccessService deploymentAccessService,
                                       PlatformAuditService platformAuditService) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentAssignmentRepository = deploymentAssignmentRepository;
        this.platformUserRepository = platformUserRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.platformAuditService = platformAuditService;
    }

    public List<DeploymentAssignmentSummary> listAssignments(String deploymentId) {
        DeploymentEntity deployment = requireDeployment(deploymentId);
        return toSummaries(deploymentAssignmentRepository.findByDeploymentIdOrderByCreatedAtAsc(deployment.getId()));
    }

    @Transactional
    public DeploymentAssignmentSummary upsertAssignment(String deploymentId, UpsertDeploymentAssignmentRequest request) {
        DeploymentEntity deployment = requireDeployment(deploymentId);
        PlatformUserEntity user = platformUserRepository.findById(request.userId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Platform user not found: " + request.userId()));
        validateAssignableUser(user);

        Instant now = Instant.now();
        DeploymentAssignmentEntity assignment = deploymentAssignmentRepository
            .findByUserIdAndDeploymentId(user.getId(), deployment.getId())
            .orElseGet(DeploymentAssignmentEntity::new);

        if (assignment.getId() == null) {
            assignment.setId("asg-" + UUID.randomUUID().toString().substring(0, 8));
            assignment.setDeploymentId(deployment.getId());
            assignment.setUserId(user.getId());
            assignment.setCreatedAt(now);
        }

        assignment.setAssignmentRole(normalizeAssignmentRole(request.assignmentRole()));
        assignment.setUpdatedAt(now);
        deploymentAssignmentRepository.save(assignment);

        platformAuditService.record(
            "DEPLOYMENT_ASSIGNMENT_UPSERTED",
            "DEPLOYMENT_ASSIGNMENT",
            assignment.getId(),
            Map.of(
                "deploymentId", deployment.getId(),
                "userId", user.getId(),
                "assignmentRole", assignment.getAssignmentRole()
            )
        );

        return toSummary(assignment, user);
    }

    @Transactional
    public void deleteAssignment(String deploymentId, String assignmentId) {
        DeploymentEntity deployment = requireDeployment(deploymentId);
        DeploymentAssignmentEntity assignment = deploymentAssignmentRepository.findById(assignmentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Assignment not found: " + assignmentId));
        if (!deployment.getId().equals(assignment.getDeploymentId())) {
            throw new ResponseStatusException(BAD_REQUEST, "Assignment does not belong to deployment: " + deploymentId);
        }

        deploymentAssignmentRepository.delete(assignment);
        platformAuditService.record(
            "DEPLOYMENT_ASSIGNMENT_DELETED",
            "DEPLOYMENT_ASSIGNMENT",
            assignment.getId(),
            Map.of(
                "deploymentId", deployment.getId(),
                "userId", assignment.getUserId()
            )
        );
    }

    @Transactional
    public void grantCreatorAccessIfNeeded(DeploymentEntity deployment) {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() == PlatformRole.PLATFORM_ADMIN) {
            return;
        }

        PlatformUserEntity user = platformUserRepository.findByEmailIgnoreCase(principal.actorId()).orElse(null);
        if (user == null) {
            return;
        }

        if (deploymentAssignmentRepository.findByUserIdAndDeploymentId(user.getId(), deployment.getId()).isPresent()) {
            return;
        }

        Instant now = Instant.now();
        DeploymentAssignmentEntity assignment = new DeploymentAssignmentEntity();
        assignment.setId("asg-" + UUID.randomUUID().toString().substring(0, 8));
        assignment.setDeploymentId(deployment.getId());
        assignment.setUserId(user.getId());
        assignment.setAssignmentRole("DEPLOYMENT_ADMIN");
        assignment.setCreatedAt(now);
        assignment.setUpdatedAt(now);
        deploymentAssignmentRepository.save(assignment);

        platformAuditService.record(
            "DEPLOYMENT_ASSIGNMENT_AUTO_GRANTED",
            "DEPLOYMENT_ASSIGNMENT",
            assignment.getId(),
            Map.of(
                "deploymentId", deployment.getId(),
                "userId", user.getId(),
                "assignmentRole", assignment.getAssignmentRole()
            )
        );
    }

    @Transactional
    public void deleteAssignmentsForDeployment(String deploymentId) {
        deploymentAssignmentRepository.deleteByDeploymentId(deploymentId);
    }

    private DeploymentEntity requireDeployment(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentAccess(deployment);
    }

    private void validateAssignableUser(PlatformUserEntity user) {
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ResponseStatusException(BAD_REQUEST, "Only active platform users can be assigned.");
        }
        if (PlatformRole.PUBLIC_API_CLIENT.name().equalsIgnoreCase(user.getRole())) {
            throw new ResponseStatusException(BAD_REQUEST, "PUBLIC_API_CLIENT cannot be assigned to deployments.");
        }
    }

    private String normalizeAssignmentRole(String assignmentRole) {
        if (!StringUtils.hasText(assignmentRole)) {
            throw new ResponseStatusException(BAD_REQUEST, "Assignment role is required.");
        }
        String normalized = assignmentRole.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "DEPLOYMENT_ADMIN", "DEPLOYMENT_EDITOR", "DEPLOYMENT_OPERATOR", "DEPLOYMENT_VIEWER" -> normalized;
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported assignment role: " + assignmentRole);
        };
    }

    private List<DeploymentAssignmentSummary> toSummaries(List<DeploymentAssignmentEntity> assignments) {
        Map<String, PlatformUserEntity> usersById = platformUserRepository.findAllById(
            assignments.stream().map(DeploymentAssignmentEntity::getUserId).distinct().toList()
        ).stream().collect(Collectors.toMap(PlatformUserEntity::getId, Function.identity()));

        return assignments.stream()
            .map(assignment -> toSummary(assignment, usersById.get(assignment.getUserId())))
            .toList();
    }

    private DeploymentAssignmentSummary toSummary(DeploymentAssignmentEntity assignment, PlatformUserEntity user) {
        return new DeploymentAssignmentSummary(
            assignment.getId(),
            assignment.getDeploymentId(),
            assignment.getUserId(),
            user == null ? "Unknown user" : user.getEmail(),
            user == null ? "Unknown user" : user.getDisplayName(),
            user == null ? "UNKNOWN" : user.getRole(),
            assignment.getAssignmentRole(),
            assignment.getCreatedAt(),
            assignment.getUpdatedAt()
        );
    }
}
