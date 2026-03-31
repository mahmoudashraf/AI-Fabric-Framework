package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentOperationApprovalEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentOperationApprovalRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentOperationApprovalSummary;
import com.ai.fabric.platform.backend.deployment.model.ResolveDeploymentOperationApprovalRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentOperationApprovalRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentOperationApprovalService {

    public static final String APPLY_VERSION = "APPLY_VERSION";
    public static final String DELETE_DEPLOYMENT = "DELETE_DEPLOYMENT";

    private static final Duration APPROVAL_TTL = Duration.ofHours(4);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentOperationApprovalRepository approvalRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final PlatformAuditService platformAuditService;

    public DeploymentOperationApprovalService(DeploymentRepository deploymentRepository,
                                              DeploymentVersionRepository deploymentVersionRepository,
                                              DeploymentOperationApprovalRepository approvalRepository,
                                              DeploymentAccessService deploymentAccessService,
                                              PlatformAuditService platformAuditService) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.approvalRepository = approvalRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.platformAuditService = platformAuditService;
    }

    @Transactional(readOnly = true)
    public List<DeploymentOperationApprovalSummary> listApprovals(String deploymentId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        PlatformPrincipal principal = requirePrincipal();
        List<DeploymentOperationApprovalEntity> approvals = principal.role() == PlatformRole.PLATFORM_ADMIN
            ? approvalRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId)
            : approvalRepository.findByDeploymentIdAndRequestedByActorIdIgnoreCaseOrderByCreatedAtDesc(
                deploymentId,
                principal.actorId()
            );
        return approvals.stream()
            .map(this::toSummary)
            .toList();
    }

    @Transactional
    public DeploymentOperationApprovalSummary createApproval(String deploymentId,
                                                             CreateDeploymentOperationApprovalRequest request) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        PlatformPrincipal principal = requirePrincipal();
        String operationType = normalizeOperationType(request.operationType());
        String targetVersionId = normalizeTargetVersionId(deploymentId, operationType, request.targetVersionId());
        assertNoOpenApproval(deploymentId, principal.actorId(), operationType, targetVersionId);

        Instant now = Instant.now();
        DeploymentOperationApprovalEntity approval = new DeploymentOperationApprovalEntity();
        approval.setId(generateId("apr"));
        approval.setDeploymentId(deploymentId);
        approval.setOperationType(operationType);
        approval.setTargetVersionId(targetVersionId);
        approval.setStatus("PENDING");
        approval.setRequestedByActorId(principal.actorId());
        approval.setRequestedByDisplayName(principal.displayName());
        approval.setRequestedReason(request.reason().trim());
        approval.setCreatedAt(now);
        approval.setUpdatedAt(now);
        approvalRepository.save(approval);

        platformAuditService.record(
            "DEPLOYMENT_OPERATION_APPROVAL_REQUESTED",
            "DEPLOYMENT_OPERATION_APPROVAL",
            approval.getId(),
            Map.of(
                "deploymentId", deployment.getId(),
                "operationType", operationType,
                "targetVersionId", targetVersionId == null ? "" : targetVersionId
            )
        );
        return toSummary(approval);
    }

    @Transactional
    public DeploymentOperationApprovalSummary approve(String approvalId,
                                                      ResolveDeploymentOperationApprovalRequest request) {
        DeploymentOperationApprovalEntity approval = approvalRepository.findById(approvalId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Approval request not found: " + approvalId));
        getDeployment(approval.getDeploymentId());
        assertPending(approval, "approved");

        PlatformPrincipal principal = requirePrincipal();
        Instant now = Instant.now();
        approval.setStatus("APPROVED");
        approval.setApprovedByActorId(principal.actorId());
        approval.setApprovedByDisplayName(principal.displayName());
        approval.setResolutionNote(normalizeOptionalText(request.note()));
        approval.setApprovedAt(now);
        approval.setExpiresAt(now.plus(APPROVAL_TTL));
        approval.setUpdatedAt(now);
        approvalRepository.save(approval);

        platformAuditService.record(
            "DEPLOYMENT_OPERATION_APPROVAL_APPROVED",
            "DEPLOYMENT_OPERATION_APPROVAL",
            approval.getId(),
            Map.of(
                "deploymentId", approval.getDeploymentId(),
                "operationType", approval.getOperationType(),
                "targetVersionId", approval.getTargetVersionId() == null ? "" : approval.getTargetVersionId()
            )
        );
        return toSummary(approval);
    }

    @Transactional
    public DeploymentOperationApprovalSummary reject(String approvalId,
                                                     ResolveDeploymentOperationApprovalRequest request) {
        DeploymentOperationApprovalEntity approval = approvalRepository.findById(approvalId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Approval request not found: " + approvalId));
        getDeployment(approval.getDeploymentId());
        assertPending(approval, "rejected");

        PlatformPrincipal principal = requirePrincipal();
        Instant now = Instant.now();
        approval.setStatus("REJECTED");
        approval.setApprovedByActorId(principal.actorId());
        approval.setApprovedByDisplayName(principal.displayName());
        approval.setResolutionNote(normalizeOptionalText(request.note()));
        approval.setRejectedAt(now);
        approval.setUpdatedAt(now);
        approvalRepository.save(approval);

        platformAuditService.record(
            "DEPLOYMENT_OPERATION_APPROVAL_REJECTED",
            "DEPLOYMENT_OPERATION_APPROVAL",
            approval.getId(),
            Map.of(
                "deploymentId", approval.getDeploymentId(),
                "operationType", approval.getOperationType(),
                "targetVersionId", approval.getTargetVersionId() == null ? "" : approval.getTargetVersionId()
            )
        );
        return toSummary(approval);
    }

    @Transactional
    public void consumeApprovedRequestIfRequired(DeploymentEntity deployment,
                                                 String operationType,
                                                 String targetVersionId,
                                                 boolean approvalRequired,
                                                 String approvalId) {
        if (!approvalRequired) {
            return;
        }

        PlatformPrincipal principal = requirePrincipal();
        if (principal.role() == PlatformRole.PLATFORM_ADMIN) {
            return;
        }

        if (!StringUtils.hasText(approvalId)) {
            throw new ResponseStatusException(
                FORBIDDEN,
                "This deployment action is protected. Request and approve an operation approval first."
            );
        }

        DeploymentOperationApprovalEntity approval = approvalRepository.findById(approvalId.trim())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Approval request not found: " + approvalId));
        if (!deployment.getId().equals(approval.getDeploymentId())) {
            throw new ResponseStatusException(FORBIDDEN, "Approval request does not belong to this deployment.");
        }
        if (!operationType.equals(approval.getOperationType())) {
            throw new ResponseStatusException(FORBIDDEN, "Approval request does not cover this deployment action.");
        }
        if (!principal.actorId().equalsIgnoreCase(approval.getRequestedByActorId())) {
            throw new ResponseStatusException(FORBIDDEN, "Approval request belongs to a different actor.");
        }
        if (!Objects.equals(targetVersionId, approval.getTargetVersionId())) {
            throw new ResponseStatusException(FORBIDDEN, "Approval request does not match the requested version.");
        }
        if (!"APPROVED".equals(approval.getStatus())) {
            throw new ResponseStatusException(FORBIDDEN, "Approval request is not approved.");
        }
        Instant now = Instant.now();
        if (approval.getExpiresAt() != null && approval.getExpiresAt().isBefore(now)) {
            approval.setStatus("EXPIRED");
            approval.setUpdatedAt(now);
            approvalRepository.save(approval);
            throw new ResponseStatusException(FORBIDDEN, "Approval request has expired.");
        }

        approval.setStatus("CONSUMED");
        approval.setConsumedAt(now);
        approval.setUpdatedAt(now);
        approvalRepository.save(approval);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("deploymentId", deployment.getId());
        details.put("operationType", operationType);
        details.put("targetVersionId", targetVersionId == null ? "" : targetVersionId);
        platformAuditService.record(
            "DEPLOYMENT_OPERATION_APPROVAL_CONSUMED",
            "DEPLOYMENT_OPERATION_APPROVAL",
            approval.getId(),
            details
        );
    }

    private DeploymentEntity getDeployment(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentAccess(deployment);
    }

    private void assertNoOpenApproval(String deploymentId,
                                      String actorId,
                                      String operationType,
                                      String targetVersionId) {
        Instant now = Instant.now();
        List<DeploymentOperationApprovalEntity> approvals = approvalRepository
            .findByDeploymentIdAndRequestedByActorIdIgnoreCaseOrderByCreatedAtDesc(deploymentId, actorId);
        for (DeploymentOperationApprovalEntity approval : approvals) {
            if (!operationType.equals(approval.getOperationType())) {
                continue;
            }
            if (!Objects.equals(targetVersionId, approval.getTargetVersionId())) {
                continue;
            }
            if ("PENDING".equals(approval.getStatus())) {
                throw new ResponseStatusException(CONFLICT, "A pending approval request already exists for this action.");
            }
            if ("APPROVED".equals(approval.getStatus())
                && (approval.getExpiresAt() == null || !approval.getExpiresAt().isBefore(now))) {
                throw new ResponseStatusException(CONFLICT, "An approved request already exists for this action.");
            }
        }
    }

    private void assertPending(DeploymentOperationApprovalEntity approval, String action) {
        if (!"PENDING".equals(approval.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Only pending approvals can be " + action + ".");
        }
    }

    private String normalizeOperationType(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(BAD_REQUEST, "operationType is required.");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!APPLY_VERSION.equals(normalized) && !DELETE_DEPLOYMENT.equals(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported operationType: " + value);
        }
        return normalized;
    }

    private String normalizeTargetVersionId(String deploymentId, String operationType, String value) {
        if (APPLY_VERSION.equals(operationType)) {
            if (!StringUtils.hasText(value)) {
                throw new ResponseStatusException(BAD_REQUEST, "targetVersionId is required for APPLY_VERSION approvals.");
            }
            DeploymentVersionEntity version = deploymentVersionRepository.findById(value.trim())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Version not found: " + value));
            if (!deploymentId.equals(version.getDeploymentId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Version does not belong to deployment: " + deploymentId);
            }
            return version.getId();
        }
        return null;
    }

    private String normalizeOptionalText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private PlatformPrincipal requirePrincipal() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null) {
            throw new ResponseStatusException(FORBIDDEN, "Platform authentication is required.");
        }
        return principal;
    }

    private DeploymentOperationApprovalSummary toSummary(DeploymentOperationApprovalEntity approval) {
        String versionLabel = null;
        if (approval.getTargetVersionId() != null) {
            versionLabel = deploymentVersionRepository.findById(approval.getTargetVersionId())
                .map(DeploymentVersionEntity::getVersionLabel)
                .orElse(approval.getTargetVersionId());
        }
        return new DeploymentOperationApprovalSummary(
            approval.getId(),
            approval.getDeploymentId(),
            approval.getOperationType(),
            approval.getTargetVersionId(),
            versionLabel,
            approval.getStatus(),
            approval.getRequestedByActorId(),
            approval.getRequestedByDisplayName(),
            approval.getRequestedReason(),
            approval.getApprovedByActorId(),
            approval.getApprovedByDisplayName(),
            approval.getResolutionNote(),
            approval.getCreatedAt(),
            approval.getUpdatedAt(),
            approval.getApprovedAt(),
            approval.getRejectedAt(),
            approval.getExpiresAt(),
            approval.getConsumedAt()
        );
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
